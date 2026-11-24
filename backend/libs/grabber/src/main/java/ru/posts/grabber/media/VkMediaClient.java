package ru.posts.grabber.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.posts.grabber.ssrf.SafeHttpFetcher;
import ru.posts.grabber.ssrf.SsrfBlockedException;
import ru.posts.grabber.ssrf.SsrfGuard;
import ru.posts.grabber.ssrf.SsrfProperties;
import ru.posts.grabber.ssrf.UrlRedactor;

/**
 * VK API calls used by the grabber media pipeline: source media download (SSRF-guarded), wall photo
 * upload ({@code photos.getWallUploadServer} → upload → {@code photos.saveWallPhoto}), {@code
 * video.get} and video upload ({@code video.save} → upload).
 */
@Component
@Profile("!openapi")
public class VkMediaClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient api;
    private final RestClient absolute;
    private final SafeHttpFetcher fetcher;
    private final SsrfProperties ssrfProperties;
    private final String apiVersion;

    public VkMediaClient(
            SafeHttpFetcher fetcher,
            SsrfProperties ssrfProperties,
            @Value("${posts.grabber.vk.base-url:https://api.vk.com}") String baseUrl,
            @Value("${posts.grabber.vk.api-version:5.199}") String apiVersion) {
        this.fetcher = fetcher;
        this.ssrfProperties = ssrfProperties;
        this.apiVersion = apiVersion;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMinutes(2));
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.api =
                RestClient.builder().baseUrl(normalized).requestFactory(factory).build();
        this.absolute = RestClient.builder().requestFactory(factory).build();
    }

    /** Downloads source media. Throws {@link MediaException} with code {@code too_large} over the limit. */
    public byte[] download(String url, long maxBytes) {
        try {
            return fetcher.fetch(url, Set.of(), ssrfProperties.getAllowedHosts(), maxBytes)
                    .body();
        } catch (SafeHttpFetcher.ResponseTooLargeException ex) {
            throw new MediaException("too_large", ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new MediaException("download_failed", UrlRedactor.stripQuery(ex.getMessage()), ex);
        }
    }

    public String uploadWallPhoto(String groupExternalId, String token, byte[] bytes, String filename) {
        String groupId = stripMinus(groupExternalId);
        MultiValueMap<String, String> serverForm = form(token);
        serverForm.add("group_id", groupId);
        JsonNode server = call("photos.getWallUploadServer", serverForm);
        String uploadUrl = server.path("response").path("upload_url").asText("");
        if (uploadUrl.isBlank()) {
            throw new MediaException("upload_failed", "VK photos.getWallUploadServer returned no upload_url");
        }
        JsonNode uploaded = multipart(uploadUrl, "photo", bytes, filename, MediaType.IMAGE_JPEG);
        if (uploaded == null || uploaded.path("photo").asText("").isBlank()) {
            throw new MediaException("upload_failed", "VK photo upload returned no photo");
        }
        MultiValueMap<String, String> saveForm = form(token);
        saveForm.add("group_id", groupId);
        saveForm.add("photo", uploaded.path("photo").asText());
        saveForm.add("server", uploaded.path("server").asText(""));
        saveForm.add("hash", uploaded.path("hash").asText(""));
        JsonNode saved = call("photos.saveWallPhoto", saveForm);
        JsonNode photo =
                saved.path("response").isArray() ? saved.path("response").path(0) : saved.path("response");
        if (!photo.hasNonNull("id") || !photo.hasNonNull("owner_id")) {
            throw new MediaException("upload_failed", "VK photos.saveWallPhoto returned no photo id");
        }
        return "photo" + photo.path("owner_id").asText() + "_"
                + photo.path("id").asText();
    }

    /** {@code video.get} for one video; empty when VK returns no item (deleted / no access). */
    public Optional<JsonNode> getVideo(String ownerId, String videoId, String accessKey, String token) {
        MultiValueMap<String, String> form = form(token);
        String key = ownerId + "_" + videoId + (accessKey == null || accessKey.isBlank() ? "" : "_" + accessKey);
        form.add("videos", key);
        form.add("count", "1");
        JsonNode body = call("video.get", form);
        JsonNode items = body.path("response").path("items");
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    public String uploadVideo(String groupExternalId, String token, byte[] bytes, String name, String description) {
        MultiValueMap<String, String> saveForm = form(token);
        saveForm.add("group_id", stripMinus(groupExternalId));
        saveForm.add("name", name == null || name.isBlank() ? "Видео" : truncate(name, 128));
        if (description != null && !description.isBlank()) {
            saveForm.add("description", truncate(description, 5000));
        }
        saveForm.add("wallpost", "0");
        JsonNode saved = call("video.save", saveForm);
        JsonNode response = saved.path("response");
        String uploadUrl = response.path("upload_url").asText("");
        if (uploadUrl.isBlank()) {
            throw new MediaException("upload_failed", "VK video.save returned no upload_url");
        }
        JsonNode uploaded =
                multipart(uploadUrl, "video_file", bytes, "video.mp4", MediaType.parseMediaType("video/mp4"));
        String owner = response.hasNonNull("owner_id")
                ? response.path("owner_id").asText()
                : "-" + stripMinus(groupExternalId);
        long videoId = response.path("video_id").asLong(0);
        if (videoId == 0 && uploaded != null) {
            videoId = uploaded.path("video_id").asLong(0);
        }
        if (videoId == 0) {
            throw new MediaException("upload_failed", "VK video upload returned no video_id");
        }
        return "video" + owner + "_" + videoId;
    }

    private JsonNode call(String method, MultiValueMap<String, String> form) {
        JsonNode body;
        try {
            body = api.post()
                    .uri("/method/" + method)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new MediaException(
                    "vk_unavailable", "VK " + method + " failed: " + UrlRedactor.stripQuery(ex.getMessage()), ex);
        }
        if (body == null) {
            throw new MediaException("vk_error", "VK " + method + " returned empty response");
        }
        if (body.has("error")) {
            JsonNode error = body.path("error");
            throw new MediaException(
                    "vk_error",
                    "VK " + method + " error " + error.path("error_code").asInt() + ": "
                            + error.path("error_msg").asText(""));
        }
        return body;
    }

    private JsonNode multipart(String uploadUrl, String part, byte[] bytes, String filename, MediaType type) {
        try {
            SsrfGuard.validateHttpUrl(uploadUrl, ssrfProperties.getAllowedHosts());
        } catch (SsrfBlockedException ex) {
            throw new MediaException(
                    "upload_failed", "VK upload URL rejected: " + UrlRedactor.stripQuery(uploadUrl), ex);
        }
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart
                .part(part, new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(type);
        String raw;
        try {
            raw = absolute.post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new MediaException(
                    "upload_failed", "VK upload failed: " + UrlRedactor.stripQuery(ex.getMessage()), ex);
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // VK upload servers may answer with text/html while the body is JSON.
            return JSON.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new MediaException("upload_failed", "VK upload returned non-JSON response", ex);
        }
    }

    private MultiValueMap<String, String> form(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", token);
        form.add("v", apiVersion);
        return form;
    }

    private static String stripMinus(String externalId) {
        String id = externalId == null ? "" : externalId.trim();
        return id.startsWith("-") ? id.substring(1) : id;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Media pipeline failure with a stable machine code (logged / stored as skip reason). */
    public static class MediaException extends RuntimeException {
        private final String code;

        public MediaException(String code, String message) {
            super(message);
            this.code = code;
        }

        public MediaException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
