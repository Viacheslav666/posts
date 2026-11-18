package ru.posts.publisher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;

/**
 * Materializes {@code scheduled_actions} after a successful publish (02-backend.md §5.3/§5.5): pin,
 * unpin, auto-delete, first comment. Story delete uses kind {@code delete_story} (BE-16).
 */
@Component
public class ScheduledActionsFactory {

    private static final Logger log = LoggerFactory.getLogger(ScheduledActionsFactory.class);

    private final ScheduledActionLifecycleJdbc actionsJdbc;
    private final ObjectMapper objectMapper;

    public ScheduledActionsFactory(ScheduledActionLifecycleJdbc actionsJdbc, ObjectMapper objectMapper) {
        this.actionsJdbc = actionsJdbc;
        this.objectMapper = objectMapper;
    }

    public void createAfterSuccess(PublicationClaim claim, String remotePostId) {
        JsonNode options = parseOptions(claim.optionsJson());
        Instant now = Instant.now();

        if (options.path("pin").asBoolean(false)) {
            actionsJdbc.insertAction(
                    claim.id(),
                    claim.runAt(),
                    claim.workspaceId(),
                    "pin",
                    now,
                    "{\"remotePostId\":\"" + escape(remotePostId) + "\"}");
        }

        Instant unpinAt = parseInstant(options, "unpinAt");
        if (unpinAt != null) {
            actionsJdbc.insertAction(
                    claim.id(),
                    claim.runAt(),
                    claim.workspaceId(),
                    "unpin",
                    unpinAt,
                    "{\"remotePostId\":\"" + escape(remotePostId) + "\"}");
        }

        Instant deleteAfter = parseInstant(options, "deleteAfter");
        if (deleteAfter == null
                && claim.ad()
                && claim.adAutoDeleteAfterPeriod()
                && claim.adDisplayPeriodDays() != null
                && claim.adDisplayPeriodDays() > 0) {
            deleteAfter = now.plusSeconds(claim.adDisplayPeriodDays() * 86_400L);
        }
        if (deleteAfter != null) {
            String kind = "story".equals(claim.contentKind()) ? "delete_story" : "delete";
            actionsJdbc.insertAction(
                    claim.id(),
                    claim.runAt(),
                    claim.workspaceId(),
                    kind,
                    deleteAfter,
                    "{\"remotePostId\":\"" + escape(remotePostId) + "\"}");
        }

        String firstComment = textOrNull(options, "firstComment");
        if (firstComment != null && !firstComment.isBlank()) {
            actionsJdbc.insertAction(
                    claim.id(),
                    claim.runAt(),
                    claim.workspaceId(),
                    "first_comment",
                    now,
                    "{\"remotePostId\":\"" + escape(remotePostId) + "\",\"text\":\"" + escape(firstComment) + "\"}");
        }
    }

    private JsonNode parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(optionsJson);
        } catch (Exception ex) {
            log.warn("Failed to parse variant options JSON: {}", ex.toString());
            return objectMapper.createObjectNode();
        }
    }

    private static Instant parseInstant(JsonNode options, String field) {
        JsonNode node = options.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String textOrNull(JsonNode options, String field) {
        JsonNode node = options.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
