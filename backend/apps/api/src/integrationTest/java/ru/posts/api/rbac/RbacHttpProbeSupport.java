package ru.posts.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.hash.ContentHasher;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelSetEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.ChannelSetRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.rbac.RbacEffect;
import ru.posts.security.rbac.RbacMatrix;
import ru.posts.security.rbac.RbacMatrixLoader;
import ru.posts.security.rbac.RbacRole;

/** Shared HTTP probe helpers for QA-6 RBAC/IDOR matrix ITs. */
final class RbacHttpProbeSupport {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final RbacMatrix MATRIX = RbacMatrixLoader.loadDefault();
    private static final ConcurrentHashMap<String, Boolean> NOT_IMPLEMENTED = new ConcurrentHashMap<>();

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final LoggingAuthMailSender mailSender;
    private final PlanRepository planRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ChannelRepository channelRepository;
    private final ChannelSetRepository channelSetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PostRepository postRepository;
    private final PostVariantRepository postVariantRepository;
    private final PublicationRepository publicationRepository;
    private final AutomationRuleRepository automationRuleRepository;
    private final GrabberItemRepository grabberItemRepository;

    RbacHttpProbeSupport(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            LoggingAuthMailSender mailSender,
            PlanRepository planRepository,
            WorkspaceRepository workspaceRepository,
            ChannelRepository channelRepository,
            ChannelSetRepository channelSetRepository,
            MediaAssetRepository mediaAssetRepository,
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            AutomationRuleRepository automationRuleRepository,
            GrabberItemRepository grabberItemRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.planRepository = planRepository;
        this.workspaceRepository = workspaceRepository;
        this.channelRepository = channelRepository;
        this.channelSetRepository = channelSetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.automationRuleRepository = automationRuleRepository;
        this.grabberItemRepository = grabberItemRepository;
    }

    static boolean isAllowed(String role, String action) {
        return MATRIX.require(action).effectFor(RbacRole.from(role)) == RbacEffect.allow;
    }

    boolean isNotImplemented(EndpointIds ids, RbacEndpointMapLoader.Endpoint endpoint) {
        return NOT_IMPLEMENTED.computeIfAbsent(endpoint.operationId(), op -> {
            try {
                // Never probe DELETE/destructive against the fixture workspace (would wipe it).
                Scope probeScope = endpoint.destructive() ? Scope.FOREIGN : Scope.OWN;
                int status = performRaw(ids.ownerToken(), endpoint, ids, probeScope);
                if (status == 501) {
                    return true;
                }
                // Stub controllers with @Valid bodies may return 400 before ApiStub (501).
                // Treat that as not-implemented for RBAC HTTP until the handler runs authz.
                if (!endpoint.implementedHint() && status == 400) {
                    return true;
                }
                return false;
            } catch (Exception ex) {
                throw new IllegalStateException("Probe failed for " + op, ex);
            }
        });
    }

    int perform(String accessToken, RbacEndpointMapLoader.Endpoint endpoint, EndpointIds ids, Scope scope)
            throws Exception {
        if (endpoint.destructive()
                && scope == Scope.OWN
                && endpoint.action() != null
                && isAllowed(ids.roleOf(accessToken), endpoint.action())) {
            return performDestructiveAllow(accessToken, endpoint, ids);
        }
        return performRaw(accessToken, endpoint, ids, scope);
    }

    private int performRaw(String accessToken, RbacEndpointMapLoader.Endpoint endpoint, EndpointIds ids, Scope scope)
            throws Exception {
        String path = resolvePath(endpoint, ids, scope);
        MockHttpServletRequestBuilder req = request(endpoint.method(), path);
        for (Map.Entry<String, String> q : resolveQuery(endpoint, ids, scope).entrySet()) {
            req.param(q.getKey(), q.getValue());
        }
        if (needsBody(endpoint.method())) {
            req.contentType(MediaType.APPLICATION_JSON).content(defaultBody(endpoint.operationId(), ids, scope));
            req.with(csrf());
        }
        if (accessToken != null) {
            req.header("Authorization", "Bearer " + accessToken);
        }
        MvcResult result = mockMvc.perform(req).andReturn();
        return result.getResponse().getStatus();
    }

    private int performDestructiveAllow(String accessToken, RbacEndpointMapLoader.Endpoint endpoint, EndpointIds ids)
            throws Exception {
        return switch (endpoint.operationId()) {
            case "workspacesDelete" -> {
                UUID disposable = createWorkspace(accessToken, "RBAC disposable " + UUID.randomUUID());
                yield statusOf(delete("/api/v1/workspaces/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "projectsDelete" -> {
                UUID disposable = createProject(accessToken, ids.workspaceId(), "Disp", "#abcdef");
                yield statusOf(delete("/api/v1/projects/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "invitationsCancel" -> {
                JsonNode inv = createInvitation(
                        accessToken,
                        ids.workspaceId(),
                        "cancel-" + UUID.randomUUID() + "@example.com",
                        "viewer",
                        List.of(ids.projectAlpha()));
                yield statusOf(delete("/api/v1/invitations/" + inv.get("id").asText())
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "workspaceMembersRemove" -> {
                // Deny-path is covered elsewhere; soft allow: attempt remove of missing member → not 403
                UUID missing = UUID.fromString("019a0000-0003-7000-8000-00000000dead");
                yield statusOf(delete("/api/v1/workspaces/" + ids.workspaceId() + "/members/" + missing)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "channelsDelete" -> {
                UUID disposable = seedChannel(ids.workspaceId(), ids.projectAlpha(), "RBAC disposable channel");
                yield statusOf(delete("/api/v1/channels/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "channelSetsDelete" -> {
                UUID disposable = seedChannelSet(ids.workspaceId(), "RBAC disposable set");
                yield statusOf(delete("/api/v1/channel-sets/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "mediaDelete" -> {
                UUID disposable = insertMedia(ids.workspaceId(), userIdFromMe(accessToken));
                yield statusOf(delete("/api/v1/media/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "postsDelete" -> {
                UUID disposable = insertPost(
                                ids.workspaceId(), ids.projectAlpha(), ids.channelId(), userIdFromMe(accessToken))
                        .postId();
                yield statusOf(delete("/api/v1/posts/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            default -> performRaw(accessToken, withoutDestructive(endpoint), ids, Scope.OWN);
        };
    }

    private static RbacEndpointMapLoader.Endpoint withoutDestructive(RbacEndpointMapLoader.Endpoint e) {
        return new RbacEndpointMapLoader.Endpoint(
                e.operationId(),
                e.method(),
                e.path(),
                e.rbac(),
                e.action(),
                e.pathParams(),
                e.query(),
                false,
                e.implementedHint());
    }

    private int statusOf(MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    private static boolean needsBody(String method) {
        return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method);
    }

    private MockHttpServletRequestBuilder request(String method, String path) {
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("Unsupported method " + method);
        };
    }

    private String resolvePath(RbacEndpointMapLoader.Endpoint endpoint, EndpointIds ids, Scope scope) {
        String path = endpoint.path();
        Map<String, String> values = placeholderValues(ids, scope);
        for (Map.Entry<String, String> e : endpoint.pathParams().entrySet()) {
            String token = "{" + e.getKey() + "}";
            String template = e.getValue();
            String resolved = values.getOrDefault(template, template);
            if (template.startsWith("{") && template.endsWith("}")) {
                resolved = values.getOrDefault(template, randomUuid());
            } else if (!template.startsWith("{")) {
                resolved = template;
            }
            path = path.replace(token, resolved);
        }
        // Fallbacks for unresolved path variables
        path = path.replace("{workspaceId}", values.get("{workspaceId}"));
        path = path.replace("{projectId}", values.get("{projectId}"));
        path = path.replace("{userId}", values.get("{memberUserId}"));
        path = path.replace("{invitationId}", values.get("{invitationId}"));
        path = path.replace("{channelId}", values.get("{channelId}"));
        path = path.replace("{setId}", values.get("{channelSetId}"));
        path = path.replace("{botId}", values.getOrDefault("{botId}", randomUuid()));
        path = path.replace("{postId}", values.get("{postId}"));
        path = path.replace("{mediaId}", values.get("{mediaId}"));
        path = path.replace("{publicationId}", values.get("{publicationId}"));
        path = path.replace("{sessionId}", values.get("{sessionId}"));
        path = path.replace("{contractorId}", randomUuid());
        path = path.replace("{contractId}", randomUuid());
        path = path.replace("{ruleId}", values.get("{ruleId}"));
        path = path.replace("{itemId}", values.get("{itemId}"));
        // confirm is tg/max-only; use tg so authz is reached after body validation
        if ("channelsConnectConfirm".equals(endpoint.operationId())) {
            path = path.replace("{network}", "tg");
        } else {
            path = path.replace("{network}", "vk");
        }
        // ORD providers use vk_ord / yandex_ord; channel OAuth uses network codes.
        if (endpoint.path().contains("/ord/")) {
            path = path.replace("{provider}", "vk_ord");
        } else {
            path = path.replace("{provider}", "vk");
        }
        return path;
    }

    private Map<String, String> resolveQuery(RbacEndpointMapLoader.Endpoint endpoint, EndpointIds ids, Scope scope) {
        Map<String, String> values = placeholderValues(ids, scope);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : endpoint.query().entrySet()) {
            String template = e.getValue();
            out.put(e.getKey(), values.getOrDefault(template, template));
        }
        if ("calendarList".equals(endpoint.operationId())) {
            out.putIfAbsent("from", "2026-12-01T00:00:00Z");
            out.putIfAbsent("to", "2026-12-31T00:00:00Z");
            out.putIfAbsent("timezone", "Europe/Moscow");
        }
        if ("ingestPreview".equals(endpoint.operationId())) {
            out.putIfAbsent("workspaceId", values.get("{workspaceId}"));
        }
        return out;
    }

    private Map<String, String> placeholderValues(EndpointIds ids, Scope scope) {
        Map<String, String> values = new LinkedHashMap<>();
        if (scope == Scope.FOREIGN) {
            values.put("{workspaceId}", ids.foreignWorkspaceId().toString());
            values.put("{projectId}", ids.foreignProjectId().toString());
            // Never reuse own resource IDs for IDOR probes.
            values.put("{memberUserId}", randomUuid());
            values.put("{invitationId}", randomUuid());
            values.put("{channelId}", randomUuid());
            values.put("{channelSetId}", randomUuid());
            values.put(
                    "{mediaId}",
                    ids.foreignMediaId() == null
                            ? randomUuid()
                            : ids.foreignMediaId().toString());
            values.put("{ruleId}", randomUuid());
            values.put("{itemId}", randomUuid());
        } else {
            values.put("{workspaceId}", ids.workspaceId().toString());
            values.put("{projectId}", ids.projectAlpha().toString());
            values.put(
                    "{memberUserId}",
                    ids.viewerUserId() == null
                            ? randomUuid()
                            : ids.viewerUserId().toString());
            values.put(
                    "{invitationId}",
                    ids.pendingInvitationId() == null
                            ? randomUuid()
                            : ids.pendingInvitationId().toString());
            values.put(
                    "{channelId}",
                    ids.channelId() == null ? randomUuid() : ids.channelId().toString());
            values.put(
                    "{channelSetId}",
                    ids.channelSetId() == null
                            ? randomUuid()
                            : ids.channelSetId().toString());
            values.put(
                    "{mediaId}",
                    ids.mediaId() == null ? randomUuid() : ids.mediaId().toString());
            values.put(
                    "{postId}",
                    ids.postId() == null ? randomUuid() : ids.postId().toString());
            values.put(
                    "{publicationId}",
                    ids.publicationId() == null
                            ? randomUuid()
                            : ids.publicationId().toString());
            values.put(
                    "{ruleId}",
                    ids.ruleId() == null ? randomUuid() : ids.ruleId().toString());
            values.put(
                    "{itemId}",
                    ids.itemId() == null ? randomUuid() : ids.itemId().toString());
        }
        values.put("{botId}", randomUuid());
        if (scope == Scope.FOREIGN) {
            values.put("{postId}", randomUuid());
            values.put("{publicationId}", randomUuid());
        }
        values.put("{sessionId}", randomUuid());
        return values;
    }

    private String defaultBody(String operationId, EndpointIds ids, Scope scope) {
        UUID workspaceId = scope == Scope.FOREIGN ? ids.foreignWorkspaceId() : ids.workspaceId();
        UUID projectId = scope == Scope.FOREIGN ? ids.foreignProjectId() : ids.projectAlpha();
        UUID channelId = scope == Scope.FOREIGN || ids.channelId() == null ? UUID.randomUUID() : ids.channelId();
        return switch (operationId) {
            case "workspacesUpdate" -> "{\"name\":\"RBAC Probe Rename\"}";
            case "projectsCreate" -> "{\"name\":\"RBAC Probe " + UUID.randomUUID() + "\",\"color\":\"#123456\"}";
            case "projectsUpdate" -> "{\"name\":\"RBAC Probe Project\",\"color\":\"#654321\"}";
            case "projectsSetMembers" -> "{\"userIds\":[]}";
            case "invitationsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("email", "rbac-invite-" + UUID.randomUUID() + "@example.com");
                node.put("role", "viewer");
                node.set("projectIds", objectMapper.createArrayNode().add(projectId.toString()));
                yield node.toString();
            }
            case "workspaceMembersUpdate" -> {
                var node = objectMapper.createObjectNode();
                node.put("role", "viewer");
                node.set("projectIds", objectMapper.createArrayNode().add(projectId.toString()));
                yield node.toString();
            }
            case "billingCheckout" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("planCode", "pro");
                node.put("period", "monthly");
                yield node.toString();
            }
            case "billingApplyPromo" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("code", "TESTPROMO");
                yield node.toString();
            }
            case "billingStartTrial" -> objectMapper
                    .createObjectNode()
                    .put("workspaceId", workspaceId.toString())
                    .toString();
            case "channelSetsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Set " + UUID.randomUUID());
                node.set("channelIds", objectMapper.createArrayNode().add(channelId.toString()));
                yield node.toString();
            }
            case "channelSetsUpdate" -> "{\"name\":\"RBAC Set Updated\"}";
            case "channelsUpdate" -> "{\"timezone\":\"Europe/Moscow\"}";
            case "channelsConnectCallback", "channelsConnectConfirm" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("projectId", projectId.toString());
                node.put("code", "rbac-probe-code");
                node.put("state", "rbac-probe-state");
                node.put("username", "@rbac_probe");
                yield node.toString();
            }
            case "mediaCreateUpload" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("kind", "image");
                node.put("mime", "image/jpeg");
                node.put("sizeBytes", 1024);
                node.put("filename", "rbac-probe.jpg");
                yield node.toString();
            }
            case "mediaImportUrl" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("url", "http://127.0.0.1/rbac-probe.jpg");
                yield node.toString();
            }
            case "mediaCompleteUpload" -> "{}";
            case "postsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("projectId", projectId.toString());
                node.put("title", "RBAC probe post");
                node.put("ad", false);
                var content = node.putObject("baseContent");
                content.put("text", "RBAC probe");
                content.putArray("mediaIds");
                var variants = node.putArray("variants");
                var variant = variants.addObject();
                variant.put("channelId", channelId.toString());
                variant.put("network", "vk");
                var vContent = variant.putObject("content");
                vContent.put("text", "RBAC probe");
                vContent.putArray("mediaIds");
                yield node.toString();
            }
            case "postsUpdate" -> {
                var node = objectMapper.createObjectNode();
                node.put("title", "RBAC probe update");
                node.put("version", ids.postVersion());
                yield node.toString();
            }
            case "postsBulk" -> {
                var node = objectMapper.createObjectNode();
                var postIds = node.putArray("postIds");
                postIds.add(
                        ids.postId() == null
                                ? UUID.randomUUID().toString()
                                : ids.postId().toString());
                node.put("action", "pause");
                yield node.toString();
            }
            case "postsPreview" -> {
                var node = objectMapper.createObjectNode();
                node.put("network", "tg");
                var content = node.putObject("content");
                content.put("text", "preview");
                content.putArray("mediaIds");
                yield node.toString();
            }
            case "postsSchedule", "calendarMove" -> {
                var node = objectMapper.createObjectNode();
                node.put("scheduledAt", "2026-12-15T12:00:00Z");
                node.put("version", ids.postVersion());
                node.put("timezone", "Europe/Moscow");
                yield node.toString();
            }
            case "postsPause", "postsResume" -> {
                var node = objectMapper.createObjectNode();
                node.put("version", ids.postVersion());
                yield node.toString();
            }
            case "channelsConnectStart",
                    "channelsStoryConnectionStart",
                    "notificationSettingsUpdate",
                    "notificationsMarkRead",
                    "notificationTelegramLinkStart" -> "{}";
            case "notificationWebPushSubscribe" -> {
                var node = objectMapper.createObjectNode();
                node.put("endpoint", "https://example.com/push/" + UUID.randomUUID());
                node.put("p256dh", "BNcRdreALRFXTkOOUHM6JtV4M4yL4k4xqHqHqHqHqHq");
                node.put("auth", "tBHItJI5svbpez7KI4CCXg");
                yield node.toString();
            }
            case "storiesBatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("projectId", projectId.toString());
                node.put("scheduledAt", "2026-12-15T12:00:00Z");
                node.put("stepMinutes", 5);
                node.put("ad", false);
                node.putArray("channelIds").add(channelId.toString());
                node.putArray("mediaIds").add(UUID.randomUUID().toString());
                var story = node.putObject("story");
                story.put("ttl", "ttl_24h");
                yield node.toString();
            }
            case "ordConnectionsUpsert" -> {
                var node = objectMapper.createObjectNode();
                node.put("apiKey", "rbac-probe-ord-key");
                node.put("publisherInn", "7707083893");
                node.put("publisherName", "RBAC Probe Publisher");
                node.put("orgType", "juridical");
                node.putArray("roles").add("publisher");
                node.put("isDefault", true);
                node.put("responsibilityAccepted", true);
                yield node.toString();
            }
            case "ordContractorsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("inn", "7707083893");
                node.put("kpp", "770701001");
                node.put("name", "RBAC Probe Contractor");
                node.put("orgType", "juridical");
                yield node.toString();
            }
            case "ordContractorsUpdate" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Probe Contractor Updated");
                yield node.toString();
            }
            case "ordContractsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("contractorId", UUID.randomUUID().toString());
                node.put("externalId", "rbac-contract-" + UUID.randomUUID());
                node.put("provider", "vk_ord");
                node.put("selfPromo", false);
                yield node.toString();
            }
            case "ordReportsRun" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("year", 2026);
                node.put("month", 10);
                yield node.toString();
            }
            case "ingestRulesCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("type", "grabber");
                node.put("name", "RBAC Grabber " + UUID.randomUUID());
                node.put("targetChannelId", channelId.toString());
                node.put("userResponsibilityAccepted", true);
                var config = node.putObject("config");
                config.putArray("sourceIds").add("-123");
                config.put("scanIntervalMinutes", 60);
                yield node.toString();
            }
            case "ingestRulesPatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Grabber Patched");
                node.put("version", ids.ruleVersion());
                yield node.toString();
            }
            case "ingestItemsApprove", "ingestItemsReject" -> "{}";
            case "ingestItemsEditAndApprove" -> {
                var node = objectMapper.createObjectNode();
                node.put("contentText", "RBAC edited grabber text");
                yield node.toString();
            }
            case "ingestItemsBulk" -> {
                var node = objectMapper.createObjectNode();
                node.putArray("itemIds")
                        .add(
                                ids.itemId() == null
                                        ? UUID.randomUUID().toString()
                                        : ids.itemId().toString());
                node.put("action", "reject");
                node.put("reason", "rbac-probe");
                yield node.toString();
            }
            case "ingestPreview" -> {
                // RSS + loopback URL: SSRF guard returns 422 after authz without network I/O.
                var node = objectMapper.createObjectNode();
                node.put("type", "rss");
                node.put("rssUrl", "http://127.0.0.1/rbac-probe-feed.xml");
                node.put("limit", 1);
                yield node.toString();
            }
            default -> "{}";
        };
    }

    EndpointIds seedTeamFixture() throws Exception {
        mailSender.clear();
        String ownerEmail = "rbac-owner-" + UUID.randomUUID() + "@example.com";
        String adminEmail = "rbac-admin-" + UUID.randomUUID() + "@example.com";
        String editorEmail = "rbac-editor-" + UUID.randomUUID() + "@example.com";
        String authorEmail = "rbac-author-" + UUID.randomUUID() + "@example.com";
        String viewerEmail = "rbac-viewer-" + UUID.randomUUID() + "@example.com";
        String foreignEmail = "rbac-foreign-" + UUID.randomUUID() + "@example.com";

        String ownerToken = registerVerifyLogin(ownerEmail, "Owner");
        registerVerifyLogin(adminEmail, "Admin");
        registerVerifyLogin(editorEmail, "Editor");
        registerVerifyLogin(authorEmail, "Author");
        registerVerifyLogin(viewerEmail, "Viewer");
        String foreignToken = registerVerifyLogin(foreignEmail, "Foreign");

        UUID workspaceId = firstWorkspaceId(ownerToken);
        upgradeToAgency(workspaceId);
        UUID projectAlpha = createProject(ownerToken, workspaceId, "Alpha", "#AA0000");
        UUID projectBeta = createProject(ownerToken, workspaceId, "Beta", "#00AA00");

        inviteAndAccept(ownerToken, workspaceId, adminEmail, "admin", List.of(projectAlpha, projectBeta));
        inviteAndAccept(ownerToken, workspaceId, editorEmail, "editor", List.of(projectAlpha));
        inviteAndAccept(ownerToken, workspaceId, authorEmail, "author", List.of(projectAlpha));
        inviteAndAccept(ownerToken, workspaceId, viewerEmail, "viewer", List.of(projectAlpha));

        UUID foreignWs = firstWorkspaceId(foreignToken);
        UUID foreignProject = createProject(foreignToken, foreignWs, "Foreign", "#0000AA");

        UUID channelId = seedChannel(workspaceId, projectAlpha, "RBAC fixture channel");
        UUID channelSetId = seedChannelSet(workspaceId, "RBAC fixture set");

        JsonNode pending = createInvitation(
                ownerToken,
                workspaceId,
                "pending-" + UUID.randomUUID() + "@example.com",
                "viewer",
                List.of(projectAlpha));

        String adminToken = login(adminEmail);
        String editorToken = login(editorEmail);
        String authorToken = login(authorEmail);
        String viewerToken = login(viewerEmail);

        UUID viewerUserId = userIdFromMe(viewerToken);
        UUID ownerUserId = userIdFromMe(ownerToken);
        UUID foreignOwnerId = userIdFromMe(foreignToken);
        UUID mediaId = insertMedia(workspaceId, ownerUserId);
        UUID foreignMediaId = insertMedia(foreignWs, foreignOwnerId);
        SeededPost seededPost = insertPost(workspaceId, projectAlpha, channelId, ownerUserId);
        SeededGrabber seededGrabber = insertGrabber(workspaceId, projectAlpha, channelId, ownerUserId);

        return new EndpointIds(
                workspaceId,
                projectAlpha,
                projectBeta,
                foreignWs,
                foreignProject,
                channelId,
                channelSetId,
                UUID.fromString(pending.get("id").asText()),
                viewerUserId,
                mediaId,
                foreignMediaId,
                seededPost.postId(),
                seededPost.publicationId(),
                seededPost.version(),
                seededGrabber.ruleId(),
                seededGrabber.itemId(),
                seededGrabber.ruleVersion(),
                ownerToken,
                adminToken,
                editorToken,
                authorToken,
                viewerToken,
                foreignToken);
    }

    private SeededGrabber insertGrabber(UUID workspaceId, UUID projectId, UUID channelId, UUID ownerId) {
        Instant now = Instant.now();
        AutomationRuleEntity rule = new AutomationRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setType("grabber");
        rule.setName("RBAC fixture grabber");
        rule.setTargetChannelId(channelId);
        rule.setStatus("active");
        rule.setConfig(GrabberConfigSupport.withDefaults(
                Map.of("source_ids", List.of("-123"), "scan_interval_minutes", 60, "publish_mode", "moderate")));
        rule.setUserResponsibilityAcceptedAt(now);
        rule.setUserResponsibilityAcceptedBy(ownerId);
        rule.setCreatedBy(ownerId);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setNextRunAt(now.plusSeconds(3600));
        automationRuleRepository.save(rule);

        GrabberItemEntity item = new GrabberItemEntity();
        item.setId(UuidV7.generate());
        item.setRuleId(rule.getId());
        item.setWorkspaceId(workspaceId);
        item.setTargetChannelId(channelId);
        item.setSourceNetwork("vk");
        item.setSourceId("-123");
        item.setRemotePostId("rbac-" + UUID.randomUUID());
        item.setSourcePermalink("https://vk.com/wall-123_1");
        item.setSourceName("RBAC source");
        item.setContentText("RBAC pending item");
        item.setContentHash(
                ContentHasher.hash(item.getContentText(), List.of(), List.of("vk:-123:" + item.getRemotePostId())));
        item.setStatus("pending_moderation");
        item.setDetectedAt(now);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        grabberItemRepository.save(item);

        return new SeededGrabber(rule.getId(), item.getId(), rule.getVersion());
    }

    private SeededPost insertPost(UUID workspaceId, UUID projectId, UUID channelId, UUID authorId) {
        Instant now = Instant.now();
        Instant scheduledAt = Instant.parse("2026-12-10T12:00:00Z");
        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(workspaceId);
        post.setProjectId(projectId);
        post.setAuthorId(authorId);
        post.setTitle("RBAC fixture post");
        post.setBaseContent(Map.of("text", "rbac", "mediaIds", List.of()));
        post.setStatus("scheduled");
        post.setScheduledAt(scheduledAt);
        post.setContentKind("post");
        post.setSourceType("manual");
        post.setTimezone("Europe/Moscow");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        postRepository.save(post);

        PostVariantEntity variant = new PostVariantEntity();
        variant.setId(UuidV7.generate());
        variant.setPostId(post.getId());
        variant.setChannelId(channelId);
        variant.setNetwork("vk");
        variant.setContent(Map.of("text", "rbac"));
        variant.setOptions(Map.of());
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        postVariantRepository.save(variant);

        PublicationEntity pub = new PublicationEntity();
        pub.setId(UuidV7.generate());
        pub.setWorkspaceId(workspaceId);
        pub.setPostId(post.getId());
        pub.setVariantId(variant.getId());
        pub.setChannelId(channelId);
        pub.setNetwork("vk");
        pub.setRunAt(scheduledAt);
        pub.setIdempotencyKey("rbac-" + post.getId());
        pub.setState("pending");
        pub.setCreatedAt(now);
        publicationRepository.save(pub);

        return new SeededPost(post.getId(), pub.getId(), post.getVersion());
    }

    private UUID insertMedia(UUID workspaceId, UUID uploaderId) {
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(UuidV7.generate());
        asset.setWorkspaceId(workspaceId);
        asset.setUploaderId(uploaderId);
        asset.setKind("image");
        asset.setMime("image/jpeg");
        asset.setSizeBytes(1024L);
        asset.setFilename("rbac-fixture.jpg");
        asset.setS3Key("workspaces/" + workspaceId + "/media/" + asset.getId() + "/rbac-fixture.jpg");
        asset.setStatus("ready");
        asset.setTags(List.of());
        asset.setDerivatives(Map.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return mediaAssetRepository.save(asset).getId();
    }

    private UUID seedChannel(UUID workspaceId, UUID projectId, String title) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setProjectId(projectId);
        channel.setNetwork("vk");
        channel.setExternalId("rbac-" + UUID.randomUUID());
        channel.setType("group");
        channel.setTitle(title);
        channel.setUsername("@rbac_" + UUID.randomUUID().toString().substring(0, 8));
        channel.setCapabilities(Map.of());
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        Instant now = Instant.now();
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        return channelRepository.save(channel).getId();
    }

    private UUID seedChannelSet(UUID workspaceId, String name) {
        ChannelSetEntity set = new ChannelSetEntity();
        set.setId(UuidV7.generate());
        set.setWorkspaceId(workspaceId);
        set.setName(name);
        Instant now = Instant.now();
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        return channelSetRepository.save(set).getId();
    }

    private UUID userIdFromMe(String token) throws Exception {
        JsonNode me = readJson(mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andReturn());
        return UUID.fromString(me.get("id").asText());
    }

    private UUID createWorkspace(String token, String name) throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andReturn());
        return UUID.fromString(created.get("id").asText());
    }

    private UUID createProject(String token, UUID workspaceId, String name, String color) throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))))
                .andReturn());
        assertThat(created.get("id")).isNotNull();
        return UUID.fromString(created.get("id").asText());
    }

    private JsonNode createInvitation(
            String ownerToken, UUID workspaceId, String email, String role, List<UUID> projectIds) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "role", role,
                                "projectIds", projectIds))))
                .andReturn());
    }

    private void inviteAndAccept(String ownerToken, UUID workspaceId, String email, String role, List<UUID> projectIds)
            throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .with(csrf())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "role", role,
                                "projectIds", projectIds))))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        String inviteToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .with(csrf())
                        .header("Authorization", bearer(login(email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", inviteToken))))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    private void upgradeToAgency(UUID workspaceId) {
        PlanEntity agency = planRepository.findByCode("agency").orElseThrow();
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setPlanId(agency.getId());
        workspaceRepository.save(workspace);
    }

    private UUID firstWorkspaceId(String accessToken) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(accessToken)))
                .andReturn());
        return UUID.fromString(list.get("items").get(0).get("id").asText());
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "name", name))))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        String verifyToken = latestMailToken(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        return login(email);
    }

    private String login(String email) throws Exception {
        JsonNode tokens = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery"))))
                .andReturn());
        return tokens.get("accessToken").asText();
    }

    private String latestMailToken(String email) {
        return mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow(() -> new AssertionError("No mail for " + email));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String randomUuid() {
        return UUID.randomUUID().toString();
    }

    enum Scope {
        OWN,
        FOREIGN
    }

    record SeededPost(UUID postId, UUID publicationId, int version) {}

    record SeededGrabber(UUID ruleId, UUID itemId, int ruleVersion) {}

    record EndpointIds(
            UUID workspaceId,
            UUID projectAlpha,
            UUID projectBeta,
            UUID foreignWorkspaceId,
            UUID foreignProjectId,
            UUID channelId,
            UUID channelSetId,
            UUID pendingInvitationId,
            UUID viewerUserId,
            UUID mediaId,
            UUID foreignMediaId,
            UUID postId,
            UUID publicationId,
            int postVersion,
            UUID ruleId,
            UUID itemId,
            int ruleVersion,
            String ownerToken,
            String adminToken,
            String editorToken,
            String authorToken,
            String viewerToken,
            String foreignToken) {

        String tokenFor(String role) {
            return switch (role) {
                case "owner" -> ownerToken;
                case "admin" -> adminToken;
                case "editor" -> editorToken;
                case "author" -> authorToken;
                case "viewer" -> viewerToken;
                default -> throw new IllegalArgumentException(role);
            };
        }

        String roleOf(String token) {
            if (token.equals(ownerToken)) {
                return "owner";
            }
            if (token.equals(adminToken)) {
                return "admin";
            }
            if (token.equals(editorToken)) {
                return "editor";
            }
            if (token.equals(authorToken)) {
                return "author";
            }
            if (token.equals(viewerToken)) {
                return "viewer";
            }
            return "owner";
        }
    }
}
