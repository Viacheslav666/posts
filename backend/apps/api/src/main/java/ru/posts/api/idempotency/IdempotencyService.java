package ru.posts.api.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.IdempotencyRequestEntity;
import ru.posts.persistence.repository.IdempotencyRequestRepository;

/**
 * Reusable API {@code Idempotency-Key} handler (02-backend.md §7).
 *
 * <p>Stores request hash + response in {@code idempotency_requests} with TTL. Same key + same body
 * → replay cached response. Same key + different body → 409 {@code idempotency_conflict}. Concurrent
 * first writers: one claims via insert; the other waits briefly for completion or gets conflict.
 *
 * <p>Intended for POSTs that create entities (media uploads, posts in BE-9, billing, …).
 */
@Service
@Profile("!openapi")
public class IdempotencyService {

    private static final String RESPONSE_BODY = "body";
    private static final String RESPONSE_HTTP_STATUS = "httpStatus";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final IdempotencyRequestRepository repository;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;
    private final TransactionTemplate requiresNew;

    public IdempotencyService(
            IdempotencyRequestRepository repository,
            javax.sql.DataSource dataSource,
            ObjectMapper objectMapper,
            IdempotencyProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.jdbcClient = JdbcClient.create(dataSource);
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs {@code action} under an optional Idempotency-Key. Blank/null key → action runs once with
     * no persistence.
     *
     * @param scope stable operation identity included in the request hash (e.g. {@code POST
     *     /api/v1/media/uploads})
     */
    public <T> T execute(
            UUID userId,
            String idempotencyKey,
            String scope,
            Object requestPayload,
            Class<T> responseType,
            Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String key = idempotencyKey.trim();
        if (key.length() > 256) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Idempotency-Key is too long");
        }
        String requestHash = hashRequest(scope, requestPayload);

        Optional<T> replay = tryReplay(userId, key, requestHash, responseType);
        if (replay.isPresent()) {
            return replay.get();
        }

        boolean claimed = claim(userId, key, requestHash);
        if (!claimed) {
            T waited = waitForSibling(userId, key, requestHash, responseType);
            if (waited != null) {
                return waited;
            }
            throw conflict("A request with this Idempotency-Key is already in progress");
        }

        try {
            T result = action.get();
            complete(userId, key, requestHash, result);
            return result;
        } catch (RuntimeException ex) {
            abandon(userId, key);
            throw ex;
        }
    }

    private <T> Optional<T> tryReplay(UUID userId, String key, String requestHash, Class<T> responseType) {
        return requiresNew.execute(status -> {
            Optional<IdempotencyRequestEntity> existing = repository.findByKeyAndUserId(key, userId);
            if (existing.isEmpty()) {
                return Optional.empty();
            }
            IdempotencyRequestEntity row = existing.get();
            if (isExpired(row)) {
                repository.deleteByKeyAndUserId(key, userId);
                return Optional.empty();
            }
            if (!requestHash.equals(row.getRequestHash())) {
                throw conflict("Idempotency-Key was reused with a different request body");
            }
            if (row.getResponse() == null || row.getResponse().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(deserializeBody(row.getResponse(), responseType));
        });
    }

    private boolean claim(UUID userId, String key, String requestHash) {
        Boolean result = requiresNew.execute(status -> {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(properties.getTtl());
            try {
                int inserted = jdbcClient
                        .sql(
                                """
                                INSERT INTO idempotency_requests ("key", user_id, request_hash, response, created_at, expires_at)
                                VALUES (:key, :userId, :requestHash, NULL, :createdAt, :expiresAt)
                                ON CONFLICT ("key", user_id) DO NOTHING
                                """)
                        .param("key", key)
                        .param("userId", userId)
                        .param("requestHash", requestHash)
                        .param("createdAt", Timestamp.from(now))
                        .param("expiresAt", Timestamp.from(expiresAt))
                        .update();
                if (inserted == 1) {
                    return true;
                }
            } catch (DataIntegrityViolationException ignored) {
                // concurrent insert lost the race
            }

            Optional<IdempotencyRequestEntity> existing = repository.findByKeyAndUserId(key, userId);
            if (existing.isEmpty()) {
                return false;
            }
            IdempotencyRequestEntity row = existing.get();
            if (isExpired(row)) {
                repository.deleteByKeyAndUserId(key, userId);
                return null; // signal retry
            }
            if (!requestHash.equals(row.getRequestHash())) {
                throw conflict("Idempotency-Key was reused with a different request body");
            }
            return false;
        });
        if (result == null) {
            return claim(userId, key, requestHash);
        }
        return result;
    }

    private void complete(UUID userId, String key, String requestHash, Object result) {
        requiresNew.executeWithoutResult(status -> {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(RESPONSE_HTTP_STATUS, HttpStatus.CREATED.value());
            envelope.put(RESPONSE_BODY, objectMapper.convertValue(result, MAP_TYPE));
            Instant now = Instant.now();
            int updated = jdbcClient
                    .sql(
                            """
                            UPDATE idempotency_requests
                            SET response = CAST(:response AS jsonb),
                                request_hash = :requestHash,
                                expires_at = :expiresAt
                            WHERE "key" = :key AND user_id = :userId
                            """)
                    .param("response", writeJson(envelope))
                    .param("requestHash", requestHash)
                    .param("expiresAt", Timestamp.from(now.plus(properties.getTtl())))
                    .param("key", key)
                    .param("userId", userId)
                    .update();
            if (updated == 0) {
                IdempotencyRequestEntity row = new IdempotencyRequestEntity();
                row.setKey(key);
                row.setUserId(userId);
                row.setRequestHash(requestHash);
                row.setResponse(envelope);
                row.setCreatedAt(now);
                row.setExpiresAt(now.plus(properties.getTtl()));
                repository.save(row);
            }
        });
    }

    private void abandon(UUID userId, String key) {
        requiresNew.executeWithoutResult(status -> repository.deleteByKeyAndUserId(key, userId));
    }

    private <T> T waitForSibling(UUID userId, String key, String requestHash, Class<T> responseType) {
        long deadline = System.nanoTime() + properties.getInFlightWait().toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> replay = tryReplay(userId, key, requestHash, responseType);
            if (replay.isPresent()) {
                return replay.get();
            }
            Boolean stillPending = requiresNew.execute(status -> {
                Optional<IdempotencyRequestEntity> existing = repository.findByKeyAndUserId(key, userId);
                if (existing.isEmpty()) {
                    return false;
                }
                IdempotencyRequestEntity row = existing.get();
                if (!requestHash.equals(row.getRequestHash())) {
                    throw conflict("Idempotency-Key was reused with a different request body");
                }
                return row.getResponse() == null || row.getResponse().isEmpty();
            });
            if (Boolean.FALSE.equals(stillPending)) {
                Optional<T> again = tryReplay(userId, key, requestHash, responseType);
                return again.orElse(null);
            }
            try {
                Thread.sleep(properties.getInFlightPoll().toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private boolean isExpired(IdempotencyRequestEntity row) {
        Instant now = Instant.now();
        if (row.getExpiresAt() != null) {
            return row.getExpiresAt().isBefore(now);
        }
        return row.getCreatedAt() != null && row.getCreatedAt().isBefore(now.minus(properties.getTtl()));
    }

    private String hashRequest(String scope, Object requestPayload) {
        try {
            String canonical = scope + "\n" + objectMapper.writeValueAsString(requestPayload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash idempotent request", ex);
        }
    }

    private <T> T deserializeBody(Map<String, Object> envelope, Class<T> responseType) {
        Object body = envelope.get(RESPONSE_BODY);
        if (body == null) {
            body = envelope;
        }
        return objectMapper.convertValue(body, responseType);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize idempotent response", ex);
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ProblemCodes.IDEMPOTENCY_CONFLICT, message);
    }
}
