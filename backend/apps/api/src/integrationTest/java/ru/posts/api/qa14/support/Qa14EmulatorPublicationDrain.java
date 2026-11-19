package ru.posts.api.qa14.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.max.MaxAdapterProperties;
import ru.posts.social.max.MaxSocialAdapter;
import ru.posts.social.ok.OkAdapterProperties;
import ru.posts.social.ok.OkSocialAdapter;
import ru.posts.social.telegram.TelegramAdapterProperties;
import ru.posts.social.telegram.TelegramSocialAdapter;
import ru.posts.social.vk.VkAdapterProperties;
import ru.posts.social.vk.VkSocialAdapter;

/**
 * Publishes pending crosspost {@code publications} rows straight to the social-emulator (no live APIs).
 */
public final class Qa14EmulatorPublicationDrain {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> ATT_LIST = new TypeReference<>() {};

    private Qa14EmulatorPublicationDrain() {}

    public static void publishDue(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            String emulatorBaseUrl,
            UUID publicationId,
            String fallbackToken)
            throws Exception {
        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT p.id, p.workspace_id, p.channel_id, p.network, p.idempotency_key, p.run_at,
                       c.external_id, pv.content::text AS content_json, pv.options::text AS options_json
                FROM publications p
                JOIN channels c ON c.id = p.channel_id
                JOIN post_variants pv ON pv.id = p.variant_id
                WHERE p.id = ?
                """,
                publicationId);
        String network = String.valueOf(row.get("network"));
        UUID workspaceId = (UUID) row.get("workspace_id");
        UUID channelId = (UUID) row.get("channel_id");
        String externalId = String.valueOf(row.get("external_id"));
        String idempotencyKey = String.valueOf(row.get("idempotency_key"));
        Map<String, Object> content = objectMapper.readValue(String.valueOf(row.get("content_json")), MAP);
        Map<String, Object> options = objectMapper.readValue(
                row.get("options_json") == null ? "{}" : String.valueOf(row.get("options_json")), MAP);

        String text = content.get("text") == null ? "" : String.valueOf(content.get("text"));
        String overflow = content.get("followUpText") == null ? null : String.valueOf(content.get("followUpText"));
        if (overflow != null && !overflow.isBlank()) {
            text = text + "\n\n" + overflow;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (!options.isEmpty()) {
            meta.put(PostVariantMeta.OPTIONS, options);
        }
        List<MediaAssetRef> inline = inlineMediaFromCrosspost(content, objectMapper);
        if (!inline.isEmpty() && "tg".equals(network)) {
            meta.put(PostVariantMeta.MEDIA, inline);
        }

        PostVariantContent variantContent = new PostVariantContent(text, null, null, null, null, meta);
        ChannelContext ctx =
                new ChannelContext(workspaceId, channelId, externalId, fallbackToken, Network.fromCode(network));
        SocialAdapter adapter = adapterFor(network, emulatorBaseUrl, objectMapper);
        PublishResult result = adapter.publish(ctx, variantContent, idempotencyKey);
        jdbc.update(
                """
                UPDATE publications
                SET state = 'succeeded', remote_post_id = ?, published_at = now()
                WHERE id = ?
                """,
                result.remotePostId(),
                publicationId);
    }

    public static void publishAllForChannels(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            String emulatorBaseUrl,
            List<UUID> channelIds,
            String fallbackToken)
            throws Exception {
        for (UUID channelId : channelIds) {
            List<UUID> pubIds = jdbc.queryForList(
                    "SELECT id FROM publications WHERE channel_id = ? AND state = 'pending' ORDER BY created_at",
                    UUID.class,
                    channelId);
            for (UUID pubId : pubIds) {
                jdbc.update(
                        "UPDATE publications SET run_at = ?, next_attempt_at = ? WHERE id = ?",
                        Timestamp.from(Instant.now().minusSeconds(5)),
                        Timestamp.from(Instant.now().minusSeconds(5)),
                        pubId);
                publishDue(jdbc, objectMapper, emulatorBaseUrl, pubId, fallbackToken);
            }
        }
    }

    private static List<MediaAssetRef> inlineMediaFromCrosspost(Map<String, Object> content, ObjectMapper objectMapper)
            throws Exception {
        Object raw = content.get("crosspostAttachments");
        if (raw == null) {
            return List.of();
        }
        List<Map<String, Object>> attachments = objectMapper.convertValue(raw, ATT_LIST);
        List<MediaAssetRef> out = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();
        int i = 0;
        for (Map<String, Object> att : attachments) {
            byte[] bytes;
            Object embedded = att.get("dataBase64");
            if (embedded != null && !embedded.toString().isBlank()) {
                bytes = Base64.getDecoder().decode(embedded.toString());
            } else {
                Object url = att.get("url");
                if (url == null || url.toString().isBlank()) {
                    continue;
                }
                bytes = fetchBytes(client, url.toString());
            }
            String type = String.valueOf(att.getOrDefault("type", "photo"));
            String mime = "video".equals(type) ? "video/mp4" : "image/jpeg";
            out.add(new MediaAssetRef("xp-att-" + i++, type, mime, bytes.length, null).withContentBytes(bytes));
        }
        return out;
    }

    private static byte[] fetchBytes(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("GET " + url + " -> " + response.statusCode());
        }
        return response.body();
    }

    private static SocialAdapter adapterFor(String network, String emulatorBaseUrl, ObjectMapper objectMapper) {
        return switch (network) {
            case "vk" -> {
                VkAdapterProperties props = new VkAdapterProperties();
                props.setBaseUrl(emulatorBaseUrl);
                props.setReconciliationMode("emulator");
                yield new VkSocialAdapter(props, objectMapper);
            }
            case "tg" -> {
                TelegramAdapterProperties props = new TelegramAdapterProperties();
                props.setBaseUrl(emulatorBaseUrl);
                yield new TelegramSocialAdapter(props, objectMapper);
            }
            case "ok" -> {
                OkAdapterProperties props = new OkAdapterProperties();
                props.setBaseUrl(emulatorBaseUrl);
                props.setReconciliationMode("emulator");
                yield new OkSocialAdapter(props, objectMapper);
            }
            case "max" -> {
                MaxAdapterProperties props = new MaxAdapterProperties();
                props.setBaseUrl(emulatorBaseUrl);
                yield new MaxSocialAdapter(props);
            }
            default -> throw new IllegalArgumentException("Unsupported network: " + network);
        };
    }
}
