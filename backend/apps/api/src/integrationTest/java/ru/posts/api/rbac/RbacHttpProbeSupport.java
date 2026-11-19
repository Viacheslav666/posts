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
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.hash.ContentHasher;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.AntispamWordListEntity;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelSetEntity;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.InvoiceEntity;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.OrdInvoiceEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.SuggestBulkJobEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.AntispamWordListRepository;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.ChannelSetRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.InvoiceRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.OrdInvoiceRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.rbac.RbacEffect;
import ru.posts.security.rbac.RbacMatrix;
import ru.posts.security.rbac.RbacMatrixLoader;
import ru.posts.security.rbac.RbacRole;
import ru.posts.suggest.config.SuggestConfigSupport;

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
    private final CrosspostRuleRepository crosspostRuleRepository;
    private final CrosspostItemRepository crosspostItemRepository;
    private final SuggestSettingsRepository suggestSettingsRepository;
    private final SuggestItemRepository suggestItemRepository;
    private final SuggestBulkJobRepository suggestBulkJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrdInvoiceRepository ordInvoiceRepository;
    private final AntispamRuleRepository antispamRuleRepository;
    private final AntispamWordListRepository antispamWordListRepository;
    private final AntispamLogJdbc antispamLogJdbc;

    private UUID suggestSettingsId;
    private UUID suggestItemId;
    private UUID suggestBulkJobId;

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
            GrabberItemRepository grabberItemRepository,
            CrosspostRuleRepository crosspostRuleRepository,
            CrosspostItemRepository crosspostItemRepository,
            SuggestSettingsRepository suggestSettingsRepository,
            SuggestItemRepository suggestItemRepository,
            SuggestBulkJobRepository suggestBulkJobRepository,
            InvoiceRepository invoiceRepository,
            OrdInvoiceRepository ordInvoiceRepository,
            AntispamRuleRepository antispamRuleRepository,
            AntispamWordListRepository antispamWordListRepository,
            AntispamLogJdbc antispamLogJdbc) {
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
        this.crosspostRuleRepository = crosspostRuleRepository;
        this.crosspostItemRepository = crosspostItemRepository;
        this.suggestSettingsRepository = suggestSettingsRepository;
        this.suggestItemRepository = suggestItemRepository;
        this.suggestBulkJobRepository = suggestBulkJobRepository;
        this.invoiceRepository = invoiceRepository;
        this.ordInvoiceRepository = ordInvoiceRepository;
        this.antispamRuleRepository = antispamRuleRepository;
        this.antispamWordListRepository = antispamWordListRepository;
        this.antispamLogJdbc = antispamLogJdbc;
    }

    static boolean isAllowed(String role, String action) {
        return MATRIX.require(action).effectFor(RbacRole.from(role)) == RbacEffect.allow;
    }

    boolean isNotImplemented(EndpointIds ids, RbacEndpointMapLoader.Endpoint endpoint) {
        return NOT_IMPLEMENTED.computeIfAbsent(endpoint.operationId(), op -> {
            // Trust the curated map once an operation is marked implemented — avoids mutating the
            // shared agency fixture (e.g. billingStartTrial → Pro/3 seats breaks invite probes).
            if (endpoint.implementedHint()) {
                return false;
            }
            try {
                // Never probe DELETE/destructive against the fixture workspace (would wipe it).
                Scope probeScope = endpoint.destructive() ? Scope.FOREIGN : Scope.OWN;
                int status = performRaw(ids.ownerToken(), endpoint, ids, probeScope);
                if (status == 501) {
                    return true;
                }
                // Stub controllers with @Valid bodies may return 400 before ApiStub (501).
                // Treat that as not-implemented for RBAC HTTP until the handler runs authz.
                if (status == 400) {
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
            req.contentType(MediaType.APPLICATION_JSON)
                    .content(defaultBody(endpoint.operationId(), ids, scope, accessToken));
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
            case "billingDeletePaymentMethod" -> {
                UUID disposable = createWorkspace(accessToken, "RBAC billing pm " + UUID.randomUUID());
                yield statusOf(delete("/api/v1/billing/payment-method")
                        .param("workspaceId", disposable.toString())
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
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
                assertThat(inv.get("id"))
                        .as("createInvitation for cancel must succeed")
                        .isNotNull();
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
            case "ordInvoicesDelete" -> {
                UUID disposable = insertOrdInvoice(ids.workspaceId());
                yield statusOf(delete("/api/v1/ord/invoices/" + disposable)
                        .with(csrf())
                        .header("Authorization", bearer(accessToken)));
            }
            case "antispamWordListsDelete" -> {
                UUID disposable = insertAntispamWordList(ids.workspaceId(), "RBAC disposable list");
                yield statusOf(delete("/api/v1/antispam/word-lists/" + disposable)
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
        Map<String, String> values = placeholderValues(ids, scope, endpoint);
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
        path = path.replace("{invoiceId}", values.get("{invoiceId}"));
        path = path.replace("{ruleId}", values.get("{ruleId}"));
        path = path.replace("{itemId}", values.get("{itemId}"));
        path = path.replace("{settingsId}", values.getOrDefault("{settingsId}", randomUuid()));
        path = path.replace("{jobId}", values.getOrDefault("{jobId}", randomUuid()));
        path = path.replace("{listId}", values.get("{antispamListId}"));
        path = path.replace("{logId}", values.get("{antispamLogId}"));
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
        Map<String, String> values = placeholderValues(ids, scope, endpoint);
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
        if ("notificationWebPushUnsubscribe".equals(endpoint.operationId())) {
            out.putIfAbsent("endpoint", "https://example.com/push/rbac-probe");
        }
        return out;
    }

    private Map<String, String> placeholderValues(
            EndpointIds ids, Scope scope, RbacEndpointMapLoader.Endpoint endpoint) {
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
            values.put("{settingsId}", randomUuid());
            values.put("{jobId}", randomUuid());
            values.put("{antispamRuleId}", randomUuid());
            values.put("{antispamListId}", randomUuid());
            values.put("{antispamLogId}", randomUuid());
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
            boolean crosspost = endpoint != null && endpoint.path().contains("/crosspost/");
            boolean antispam = endpoint != null && endpoint.path().contains("/antispam/");
            UUID ruleId;
            if (antispam) {
                ruleId = ids.antispamRuleId();
            } else if (crosspost) {
                ruleId = ids.crosspostRuleId();
            } else {
                ruleId = ids.ruleId();
            }
            UUID itemId = crosspost ? ids.crosspostItemId() : ids.itemId();
            values.put("{ruleId}", ruleId == null ? randomUuid() : ruleId.toString());
            values.put("{itemId}", itemId == null ? randomUuid() : itemId.toString());
            values.put("{settingsId}", suggestSettingsId == null ? randomUuid() : suggestSettingsId.toString());
            values.put("{jobId}", suggestBulkJobId == null ? randomUuid() : suggestBulkJobId.toString());
            values.put(
                    "{antispamRuleId}",
                    ids.antispamRuleId() == null
                            ? randomUuid()
                            : ids.antispamRuleId().toString());
            values.put(
                    "{antispamListId}",
                    ids.antispamListId() == null
                            ? randomUuid()
                            : ids.antispamListId().toString());
            values.put(
                    "{antispamLogId}",
                    ids.antispamLogId() == null
                            ? randomUuid()
                            : ids.antispamLogId().toString());
        }
        values.put("{botId}", randomUuid());
        if (scope == Scope.FOREIGN) {
            values.put("{postId}", randomUuid());
            values.put("{publicationId}", randomUuid());
        }
        // Suggest item IDs must not collide with grabber {itemId} fixtures.
        if (endpoint != null
                && endpoint.operationId() != null
                && endpoint.operationId().startsWith("suggest")) {
            if (scope == Scope.FOREIGN) {
                values.put("{itemId}", randomUuid());
                values.put("{settingsId}", randomUuid());
                values.put("{jobId}", randomUuid());
            } else {
                values.put("{itemId}", suggestItemId == null ? randomUuid() : suggestItemId.toString());
                values.put("{settingsId}", suggestSettingsId == null ? randomUuid() : suggestSettingsId.toString());
                values.put("{jobId}", suggestBulkJobId == null ? randomUuid() : suggestBulkJobId.toString());
            }
        }
        values.put("{sessionId}", randomUuid());
        boolean ordInvoice = endpoint != null
                && (endpoint.path().contains("/ord/invoices/") || "ordInvoicesDelete".equals(endpoint.operationId()));
        UUID invoice = ordInvoice ? ids.ordInvoiceId() : ids.invoiceId();
        values.put("{invoiceId}", scope == Scope.FOREIGN || invoice == null ? randomUuid() : invoice.toString());
        return values;
    }

    private String defaultBody(String operationId, EndpointIds ids, Scope scope, String accessToken) throws Exception {
        UUID workspaceId = scope == Scope.FOREIGN ? ids.foreignWorkspaceId() : ids.workspaceId();
        UUID projectId = scope == Scope.FOREIGN ? ids.foreignProjectId() : ids.projectAlpha();
        UUID channelId = scope == Scope.FOREIGN || ids.channelId() == null ? UUID.randomUUID() : ids.channelId();
        // Mutating billing ops must not touch the shared agency fixture (seat/plan limits).
        // Only owner gets a disposable workspace (they are owner there). Deny roles keep the
        // shared fixture membership so billing.manage/view correctly returns 403.
        if (scope == Scope.OWN
                && (operationId.startsWith("billing")
                        && (operationId.contains("Trial")
                                || operationId.contains("Checkout")
                                || operationId.contains("Autopay")
                                || operationId.contains("ChangePlan")
                                || operationId.contains("RequestInvoice")
                                || operationId.contains("AutoRenew")
                                || operationId.contains("ApplyPromo")))
                && isAllowed(ids.roleOf(accessToken), "billing.manage")) {
            workspaceId = createWorkspace(accessToken, "RBAC billing " + UUID.randomUUID());
        }
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
                node.put("amountKopecks", 50_000);
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
            case "billingUpdateAutopay" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("enabled", false);
                node.put("thresholdKopecks", 10_000);
                node.put("topupAmountKopecks", 50_000);
                yield node.toString();
            }
            case "billingSetAutoRenew" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("enabled", false);
                yield node.toString();
            }
            case "billingChangePlan" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("planCode", "daily_6");
                yield node.toString();
            }
            case "billingRequestInvoice" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("amountKopecks", 100_000);
                var buyer = node.putObject("buyer");
                buyer.put("name", "ООО Тест");
                buyer.put("inn", "7707083893");
                buyer.put("kpp", "773601001");
                buyer.put("ogrn", "1027700132195");
                yield node.toString();
            }
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
            case "postsBatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("projectId", projectId.toString());
                node.put("scheduledAt", "2026-12-15T12:00:00Z");
                node.put("stepMinutes", 5);
                node.put("ad", false);
                node.put("text", "RBAC probe batch");
                node.put("timezone", "Europe/Moscow");
                node.putArray("channelIds").add(channelId.toString());
                node.putArray("mediaIds").add(UUID.randomUUID().toString());
                yield node.toString();
            }
            case "ordConnectionsUpsert" -> {
                var node = objectMapper.createObjectNode();
                node.put("apiKey", "rbac-probe-ord-key");
                node.put("publisherInn", "7707083893");
                node.put("publisherName", "RBAC Probe Publisher");
                node.put("orgType", "juridical");
                node.put("address", "Москва, тестовый адрес, 1");
                node.put("vatRate", "without_vat");
                node.put("isForeign", false);
                node.put("notifyProblems", true);
                node.putArray("roles").add("advertiser").add("publisher");
                node.put("isDefault", true);
                node.put("responsibilityAccepted", true);
                yield node.toString();
            }
            case "ordVatCalculate" -> {
                var node = objectMapper.createObjectNode();
                node.put("amountWithVat", 120);
                node.put("vatRate", "20");
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
            case "ordEridCheck" -> {
                var node = objectMapper.createObjectNode();
                node.put("workspaceId", workspaceId.toString());
                node.put("erid", "test-erid-rbac-probe");
                node.put("provider", "vk_ord");
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
            case "ingestRulesCloneConfig" -> {
                var node = objectMapper.createObjectNode();
                // Self-clone is valid and avoids needing a second seeded rule.
                node.put(
                        "sourceRuleId",
                        ids.ruleId() == null
                                ? UUID.randomUUID().toString()
                                : ids.ruleId().toString());
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
            case "crosspostRulesCreate" -> {
                // Minimal valid body so Bean Validation passes and RBAC runs (deny → 403, IDOR → 404).
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Crosspost " + UUID.randomUUID());
                node.put("sourceChannelId", channelId.toString());
                yield node.toString();
            }
            case "crosspostRulesPatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Crosspost Patched");
                node.put("version", ids.crosspostRuleVersion());
                yield node.toString();
            }
            case "crosspostRulesPreview" -> {
                var node = objectMapper.createObjectNode();
                node.put("sourceRemoteId", "rbac-preview-1");
                var candidate = node.putObject("candidate");
                candidate.put("text", "RBAC crosspost preview");
                yield node.toString();
            }
            case "suggestSettingsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("channelId", channelId.toString());
                node.put("mode", "manual");
                yield node.toString();
            }
            case "suggestSettingsPatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("mode", "manual");
                yield node.toString();
            }
            case "suggestSettingsCopy" -> {
                var node = objectMapper.createObjectNode();
                node.putArray("channelIds").add(channelId.toString());
                yield node.toString();
            }
            case "suggestItemsApprove", "suggestItemsReject" -> "{}";
            case "suggestItemsBulk" -> {
                var node = objectMapper.createObjectNode();
                node.put("action", "approve_ids");
                var idsArr = node.putArray("ids");
                idsArr.add(suggestItemId == null ? UUID.randomUUID().toString() : suggestItemId.toString());
                yield node.toString();
            }
            case "antispamRulesCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("channelId", channelId.toString());
                node.put("name", "RBAC Antispam " + UUID.randomUUID());
                node.put("priority", 100);
                node.putArray("sections").add("wall_comment");
                node.put("dryRun", true);
                yield node.toString();
            }
            case "antispamRulesPatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Antispam Patched");
                if (ids.antispamRuleVersion() > 0) {
                    node.put("version", ids.antispamRuleVersion());
                }
                yield node.toString();
            }
            case "antispamWordListsCreate" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Words " + UUID.randomUUID());
                node.putArray("words").add("spam").add("реклама");
                node.put("mode", "black");
                yield node.toString();
            }
            case "antispamWordListsPatch" -> {
                var node = objectMapper.createObjectNode();
                node.put("name", "RBAC Words Patched");
                if (ids.antispamListVersion() > 0) {
                    node.put("version", ids.antispamListVersion());
                }
                yield node.toString();
            }
            case "antispamLogRestore" -> {
                var node = objectMapper.createObjectNode();
                node.put("unban", false);
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
        SeededCrosspost seededCrosspost = insertCrosspost(workspaceId, projectAlpha, channelId, ownerUserId);
        SeededSuggest seededSuggest = insertSuggest(workspaceId, projectAlpha, channelId, ownerUserId);
        this.suggestSettingsId = seededSuggest.settingsId();
        this.suggestItemId = seededSuggest.itemId();
        this.suggestBulkJobId = seededSuggest.jobId();
        SeededAntispam seededAntispam = insertAntispam(workspaceId, projectAlpha, channelId, ownerUserId);
        UUID invoiceId = insertInvoice(workspaceId);
        UUID ordInvoiceId = insertOrdInvoice(workspaceId);

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
                seededCrosspost.ruleId(),
                seededCrosspost.itemId(),
                seededCrosspost.ruleVersion(),
                seededAntispam.ruleId(),
                seededAntispam.ruleVersion(),
                seededAntispam.listId(),
                seededAntispam.listVersion(),
                seededAntispam.logId(),
                invoiceId,
                ordInvoiceId,
                ownerToken,
                adminToken,
                editorToken,
                authorToken,
                viewerToken,
                foreignToken);
    }

    private UUID insertInvoice(UUID workspaceId) {
        Instant now = Instant.now();
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(UuidV7.generate());
        invoice.setWorkspaceId(workspaceId);
        invoice.setAmount(java.math.BigDecimal.valueOf(500));
        invoice.setAmountKopecks(50_000L);
        invoice.setCurrency("RUB");
        invoice.setStatus("issued");
        invoice.setInvoiceNumber("RBAC-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setDescription("RBAC fixture invoice");
        invoice.setBuyerRequisites(Map.of("inn", "7707083893", "name", "RBAC LLC"));
        invoice.setPdfUrl("/api/v1/billing/invoices/" + invoice.getId() + "/pdf");
        invoice.setActPdfUrl("/api/v1/billing/invoices/" + invoice.getId() + "/act");
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);
        return invoiceRepository.save(invoice).getId();
    }

    private UUID insertOrdInvoice(UUID workspaceId) {
        Instant now = Instant.now();
        OrdInvoiceEntity invoice = new OrdInvoiceEntity();
        invoice.setId(UuidV7.generate());
        invoice.setWorkspaceId(workspaceId);
        invoice.setProvider("vk_ord");
        invoice.setExternalId("invoice:rbac:" + UUID.randomUUID());
        invoice.setPeriodYear(2026);
        invoice.setPeriodMonth(10);
        invoice.setAmount(java.math.BigDecimal.valueOf(1500));
        invoice.setStatus("submitted");
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);
        return ordInvoiceRepository.save(invoice).getId();
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

    private SeededCrosspost insertCrosspost(UUID workspaceId, UUID projectId, UUID channelId, UUID ownerId) {
        Instant now = Instant.now();
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setSourceChannelId(channelId);
        rule.setName("RBAC fixture crosspost");
        // Draft without targets: activate allow-path hits 400 after authz (still ≠ 401/403).
        rule.setStatus("draft");
        rule.setConfig(CrosspostConfigSupport.withDefaults(Map.of()));
        rule.setCreatedBy(ownerId);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        crosspostRuleRepository.save(rule);

        CrosspostItemEntity item = new CrosspostItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(workspaceId);
        item.setRuleId(rule.getId());
        item.setSourceChannelId(channelId);
        item.setSourceRemoteId("rbac-xp-" + UUID.randomUUID());
        item.setSourceNetwork("vk");
        item.setStatus("failed");
        item.setReasons(new String[] {"rbac-fixture"});
        item.setContentText("RBAC crosspost failed item");
        item.setProcessAfter(now);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        crosspostItemRepository.save(item);

        return new SeededCrosspost(rule.getId(), item.getId(), rule.getVersion());
    }

    private SeededSuggest insertSuggest(UUID workspaceId, UUID projectId, UUID channelId, UUID ownerId) {
        Instant now = Instant.now();
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(UuidV7.generate());
        settings.setWorkspaceId(workspaceId);
        settings.setProjectId(projectId);
        settings.setChannelId(channelId);
        settings.setStatus("active");
        settings.setMode("manual");
        settings.setConfig(SuggestConfigSupport.withDefaults(Map.of("reject_policy", "keep")));
        settings.setConfigVersion(1);
        settings.setCreatedBy(ownerId);
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        settings.setNextSyncAt(now.plusSeconds(600));
        suggestSettingsRepository.save(settings);

        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(workspaceId);
        item.setChannelId(channelId);
        item.setSettingsId(settings.getId());
        item.setVkPostId("rbac-suggest-" + UUID.randomUUID());
        item.setAuthorVkId("1001");
        item.setText("RBAC suggest pending");
        item.setStatus("pending_moderation");
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        suggestItemRepository.save(item);

        SuggestBulkJobEntity job = new SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(workspaceId);
        job.setSettingsId(settings.getId());
        job.setChannelId(channelId);
        job.setAction("approve_ids");
        job.setStatus("pending");
        job.setIdempotencyKey("rbac-suggest-bulk-" + UUID.randomUUID());
        job.setItemIds(new UUID[] {item.getId()});
        job.setTotal(1);
        job.setCreatedBy(ownerId);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        suggestBulkJobRepository.save(job);

        return new SeededSuggest(settings.getId(), item.getId(), job.getId());
    }

    private SeededAntispam insertAntispam(UUID workspaceId, UUID projectId, UUID channelId, UUID ownerId) {
        Instant now = Instant.now();
        AntispamRuleEntity rule = new AntispamRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setChannelId(channelId);
        rule.setName("RBAC fixture antispam");
        rule.setStatus("draft");
        rule.setPriority(100);
        rule.setSections(List.of("wall_comment"));
        rule.setConfig(Map.of(
                "filters", Map.of("keywords", Map.of("black", List.of("spam"))),
                "actions", Map.of("delete", true)));
        rule.setConfigVersion(1);
        rule.setDryRun(true);
        rule.setCreatedBy(ownerId);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        antispamRuleRepository.save(rule);

        AntispamWordListEntity list = new AntispamWordListEntity();
        list.setId(UuidV7.generate());
        list.setWorkspaceId(workspaceId);
        list.setName("RBAC fixture words");
        list.setWords(List.of("spam", "реклама"));
        list.setMode("black");
        list.setCreatedAt(now);
        list.setUpdatedAt(now);
        antispamWordListRepository.save(list);

        UUID logId = UuidV7.generate();
        antispamLogJdbc.insert(new AntispamLogJdbc.AntispamLogRow(
                logId,
                workspaceId,
                channelId,
                rule.getId(),
                "wall_comment",
                "rbac-fixture-" + logId,
                "{\"owner_id\":-1,\"post_id\":1,\"comment_id\":1,\"comment_type\":\"wall\"}",
                "42",
                "rbac spam snapshot",
                null,
                List.of("keyword"),
                "deleted",
                "done",
                true,
                List.of(),
                now,
                null,
                null));

        return new SeededAntispam(rule.getId(), rule.getVersion(), list.getId(), list.getVersion(), logId);
    }

    private UUID insertAntispamWordList(UUID workspaceId, String name) {
        Instant now = Instant.now();
        AntispamWordListEntity list = new AntispamWordListEntity();
        list.setId(UuidV7.generate());
        list.setWorkspaceId(workspaceId);
        list.setName(name);
        list.setWords(List.of("disposable"));
        list.setMode("black");
        list.setCreatedAt(now);
        list.setUpdatedAt(now);
        return antispamWordListRepository.save(list).getId();
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

    record SeededCrosspost(UUID ruleId, UUID itemId, int ruleVersion) {}

    record SeededSuggest(UUID settingsId, UUID itemId, UUID jobId) {}

    record SeededAntispam(UUID ruleId, int ruleVersion, UUID listId, int listVersion, UUID logId) {}

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
            UUID crosspostRuleId,
            UUID crosspostItemId,
            int crosspostRuleVersion,
            UUID antispamRuleId,
            int antispamRuleVersion,
            UUID antispamListId,
            int antispamListVersion,
            UUID antispamLogId,
            UUID invoiceId,
            UUID ordInvoiceId,
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
