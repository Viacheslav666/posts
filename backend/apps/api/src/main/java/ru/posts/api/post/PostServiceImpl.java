package ru.posts.api.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.channel.NetworkCapabilitiesService;
import ru.posts.api.channel.PlanLimitService;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.ord.AdMarkingValidator;
import ru.posts.api.ord.OrdService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.network.NetworkDtos;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.content.ContentRenderRequest;
import ru.posts.content.ContentRenderResult;
import ru.posts.content.ContentRenderer;
import ru.posts.content.DefaultContentRenderer;
import ru.posts.content.NetworkTextLimits;
import ru.posts.ord.api.AdMarker;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.OrdConnectionEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.OrdConnectionRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.security.rbac.RbacRole;

@Service
@Profile("!openapi")
@Transactional
public class PostServiceImpl implements PostService {

    static final String IDEMPOTENCY_SCOPE_CREATE = "POST /api/v1/posts";

    private final PostRepository postRepository;
    private final PostVariantRepository postVariantRepository;
    private final PublicationRepository publicationRepository;
    private final ChannelRepository channelRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final OutboxRepository outboxRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;
    private final IdempotencyService idempotencyService;
    private final PlanLimitService planLimitService;
    private final NetworkCapabilitiesService networkCapabilitiesService;
    private final OrdConnectionRepository ordConnectionRepository;
    private final OrdService ordService;
    private final ObjectMapper objectMapper;
    private final ContentRenderer contentRenderer;
    private final TransactionTemplate writeTx;

    public PostServiceImpl(
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            ChannelRepository channelRepository,
            MediaAssetRepository mediaAssetRepository,
            OutboxRepository outboxRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder,
            IdempotencyService idempotencyService,
            PlanLimitService planLimitService,
            NetworkCapabilitiesService networkCapabilitiesService,
            OrdConnectionRepository ordConnectionRepository,
            OrdService ordService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.channelRepository = channelRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.outboxRepository = outboxRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
        this.idempotencyService = idempotencyService;
        this.planLimitService = planLimitService;
        this.networkCapabilitiesService = networkCapabilitiesService;
        this.ordConnectionRepository = ordConnectionRepository;
        this.ordService = ordService;
        this.objectMapper = objectMapper;
        this.contentRenderer = new DefaultContentRenderer();
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.PostPage list(
            UUID userId,
            UUID workspaceId,
            Instant from,
            Instant to,
            ApiEnums.PostStatus status,
            UUID channelId,
            UUID projectId,
            ApiEnums.Network network,
            UUID authorId,
            String q,
            PostDtos.PostSort sort,
            PostDtos.ContentKind contentKind,
            CursorPage.Params page) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "posts.view");
        if (projectId != null) {
            access.requireVisibleProject(membership, projectId);
        }
        List<PostEntity> found = new ArrayList<>(postRepository.search(
                workspaceId,
                from,
                to,
                status == null ? null : status.name(),
                projectId,
                authorId,
                channelId,
                network == null ? null : network.name(),
                blankToNull(q)));
        found = new ArrayList<>(filterVisibleProjects(membership, found));
        if (contentKind != null) {
            found = found.stream()
                    .filter(p -> contentKind.name().equals(p.getContentKind() == null ? "post" : p.getContentKind()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        sortPosts(found, sort);
        int limit = page == null ? 20 : page.limitOrDefault();
        String cursor = page == null ? null : page.cursor();
        CursorPages.Slice<PostEntity> slice = CursorPages.slice(found, cursor, limit, PostEntity::getId);
        Map<UUID, List<PostVariantEntity>> variantsByPost = loadVariants(slice.items());
        List<PostDtos.Post> items = slice.items().stream()
                .map(p -> toDto(p, variantsByPost.getOrDefault(p.getId(), List.of())))
                .toList();
        return new CursorPage.PostPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public PostDtos.Post get(UUID userId, UUID postId) {
        PostEntity post = requirePost(postId);
        WorkspaceMembership membership = access.requirePermission(post.getWorkspaceId(), userId, "posts.view");
        ensureProjectVisible(membership, post);
        return toDto(post, postVariantRepository.findByPostId(postId));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostDtos.Post create(
            UUID userId, PostDtos.CreatePostRequest request, String idempotencyKey, ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_CREATE,
                request,
                PostDtos.Post.class,
                () -> writeTx.execute(status -> createOnce(userId, request, ctx)));
    }

    private PostDtos.Post createOnce(UUID userId, PostDtos.CreatePostRequest request, ClientRequestContext ctx) {
        WorkspaceMembership membership = access.requirePermission(request.workspaceId(), userId, "posts.create");
        if (request.projectId() != null) {
            access.requireVisibleProject(membership, request.projectId());
        }
        planLimitService.ensureCanCreatePost(request.workspaceId());
        if (request.contentKind() == PostDtos.ContentKind.story) {
            planLimitService.ensureCanCreateStory(request.workspaceId());
        }

        List<PostDtos.PostVariantUpsert> variants = request.variants() == null ? List.of() : request.variants();
        validateVariants(request.workspaceId(), variants);
        validateMedia(request.workspaceId(), PostContentMapper.mediaIds(request.baseContent()));
        for (PostDtos.PostVariantUpsert v : variants) {
            validateMedia(request.workspaceId(), PostContentMapper.mediaIds(v.content()));
            AdMarkingValidator.validateNetworkOptions(v.network(), v.options());
        }
        validateContentAgainstNetworks(request.baseContent(), variants);
        Map<String, Object> adPayload = resolveAdPayload(request.ad(), request.adMarking(), request.adPayload());
        if (request.ad()) {
            OrdDtos.AdMarking marking = request.adMarking() != null
                    ? request.adMarking()
                    : objectMapper.convertValue(adPayload, OrdDtos.AdMarking.class);
            AdMarkingValidator.requireValidWhenAd(true, marking);
        }

        Instant now = Instant.now();
        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(request.workspaceId());
        post.setProjectId(request.projectId());
        post.setAuthorId(userId);
        post.setTitle(
                blankToNull(request.title()) == null
                        ? PostContentMapper.autoTitle(request.baseContent())
                        : request.title().trim());
        Map<String, Object> base =
                new LinkedHashMap<>(PostContentMapper.contentToMap(request.baseContent(), objectMapper));
        if (request.repost() != null) {
            base.put("repost", objectMapper.convertValue(request.repost(), Map.class));
        }
        post.setBaseContent(base);
        post.setScheduledAt(request.scheduledAt());
        post.setRecurrence(PostContentMapper.recurrenceToMap(request.recurrence(), objectMapper));
        post.setAd(request.ad());
        post.setAdPayload(adPayload);
        post.setStopAt(request.stopAt());
        post.setRemoveAfterPublish(Boolean.TRUE.equals(request.removeAfterPublish()));
        post.setTimezone(resolveTimezone(request.timezone()));
        post.setContentKind(
                request.contentKind() == null ? "post" : request.contentKind().name());
        applyStoryFields(post, request.story(), variants, request.baseContent(), request.workspaceId());
        post.setSourceType("manual");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        boolean scheduleNow = request.scheduledAt() != null;
        post.setStatus(scheduleNow ? "scheduled" : "draft");
        if (scheduleNow) {
            access.requireAction(
                    membership, "posts.schedule", ru.posts.security.rbac.RbacEvaluationContext.workspaceOnly());
            ensureCanScheduleAd(membership, post);
            if (StorySupport.isStory(post.getContentKind())) {
                validateStoryForSchedule(post, variants, request.baseContent());
            }
        }
        postRepository.save(post);
        List<PostVariantEntity> savedVariants =
                saveVariants(post.getId(), enrichVariantsForStory(post, variants, request.story()));

        if (scheduleNow) {
            if (post.isAd()) {
                ordService.registerCreative(userId, post.getId(), ctx);
                post = requirePost(post.getId());
                savedVariants = postVariantRepository.findByPostId(post.getId());
            }
            materializePublications(post, savedVariants, now);
            writeOutbox(post, StorySupport.isStory(post.getContentKind()) ? "story.scheduled" : "post.scheduled");
            planLimitService.recordPostCreated(request.workspaceId());
        }

        auditRecorder.record(
                post.getWorkspaceId(),
                userId,
                StorySupport.isStory(post.getContentKind()) ? "story.created" : "post.create",
                "post",
                post.getId(),
                Map.of("status", post.getStatus(), "contentKind", post.getContentKind()),
                ctx == null ? null : ctx.ip());
        return toDto(post, savedVariants);
    }

    @Override
    public PostDtos.Post update(
            UUID userId, UUID postId, PostDtos.UpdatePostRequest request, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        WorkspaceMembership membership = access.requirePermission(post.getWorkspaceId(), userId, "posts.update");
        ensureProjectVisible(membership, post);
        assertVersion(post, request.version());

        if (request.projectId() != null) {
            access.requireVisibleProject(membership, request.projectId());
            post.setProjectId(request.projectId());
        }
        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.baseContent() != null) {
            Map<String, Object> base =
                    new LinkedHashMap<>(PostContentMapper.contentToMap(request.baseContent(), objectMapper));
            if (request.repost() != null) {
                base.put("repost", objectMapper.convertValue(request.repost(), Map.class));
            }
            post.setBaseContent(base);
            validateMedia(post.getWorkspaceId(), PostContentMapper.mediaIds(request.baseContent()));
        } else if (request.repost() != null) {
            Map<String, Object> base = new LinkedHashMap<>(post.getBaseContent());
            base.put("repost", objectMapper.convertValue(request.repost(), Map.class));
            post.setBaseContent(base);
        }
        if (request.scheduledAt() != null) {
            post.setScheduledAt(request.scheduledAt());
        }
        if (request.recurrence() != null) {
            post.setRecurrence(PostContentMapper.recurrenceToMap(request.recurrence(), objectMapper));
        }
        if (request.ad() != null) {
            post.setAd(request.ad());
        }
        if (request.adMarking() != null || request.adPayload() != null) {
            boolean adFlag = request.ad() != null ? request.ad() : post.isAd();
            Map<String, Object> adPayload = resolveAdPayload(adFlag, request.adMarking(), request.adPayload());
            if (adFlag && request.adMarking() != null) {
                AdMarkingValidator.requireValidWhenAd(true, request.adMarking());
            }
            post.setAdPayload(adPayload);
        }
        if (request.stopAt() != null) {
            post.setStopAt(request.stopAt());
        }
        if (request.removeAfterPublish() != null) {
            post.setRemoveAfterPublish(request.removeAfterPublish());
        }
        if (request.timezone() != null) {
            post.setTimezone(resolveTimezone(request.timezone()));
        }
        if (request.story() != null && StorySupport.isStory(post.getContentKind())) {
            applyStoryFields(post, request.story(), request.variants(), request.baseContent(), post.getWorkspaceId());
        }

        List<PostVariantEntity> variants;
        if (request.variants() != null) {
            validateVariants(post.getWorkspaceId(), request.variants());
            for (PostDtos.PostVariantUpsert v : request.variants()) {
                validateMedia(post.getWorkspaceId(), PostContentMapper.mediaIds(v.content()));
                AdMarkingValidator.validateNetworkOptions(v.network(), v.options());
            }
            deleteVariants(post.getId());
            variants = saveVariants(post.getId(), enrichVariantsForStory(post, request.variants(), request.story()));
        } else {
            variants = postVariantRepository.findByPostId(post.getId());
        }

        Instant now = Instant.now();
        post.setUpdatedAt(now);
        if ("scheduled".equals(post.getStatus()) || "paused".equals(post.getStatus())) {
            cancelPendingPublications(post.getId());
            if ("scheduled".equals(post.getStatus()) && post.getScheduledAt() != null) {
                materializePublications(post, variants, now);
                writeOutbox(post, "post.rescheduled");
            }
        }
        postRepository.save(post);
        auditRecorder.record(
                post.getWorkspaceId(),
                userId,
                "post.update",
                "post",
                post.getId(),
                Map.of("version", post.getVersion()),
                ctx == null ? null : ctx.ip());
        return toDto(post, variants);
    }

    @Override
    public AuthDtos.MessageResponse delete(UUID userId, UUID postId, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        access.requirePermission(post.getWorkspaceId(), userId, "posts.delete");
        Instant now = Instant.now();
        post.setDeletedAt(now);
        post.setStatus("archived");
        post.setUpdatedAt(now);
        cancelPendingPublications(postId);
        postRepository.save(post);
        writeOutbox(post, "post.deleted");
        auditRecorder.record(
                post.getWorkspaceId(), userId, "post.delete", "post", postId, Map.of(), ctx == null ? null : ctx.ip());
        return new AuthDtos.MessageResponse("deleted");
    }

    @Override
    public PostDtos.Post schedule(
            UUID userId, UUID postId, PostDtos.SchedulePostRequest request, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        WorkspaceMembership membership = access.requirePermission(post.getWorkspaceId(), userId, "posts.schedule");
        ensureProjectVisible(membership, post);
        assertVersion(post, request.version());
        if ("pending_moderation".equals(post.getModerationStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Post awaits grabber moderation (BE-17)");
        }
        List<PostVariantEntity> variants = postVariantRepository.findByPostId(postId);
        if (variants.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, "Post has no channel variants");
        }
        if (StorySupport.isStory(post.getContentKind())) {
            List<PostDtos.PostVariantUpsert> upserts = variants.stream()
                    .map(v -> new PostDtos.PostVariantUpsert(
                            v.getId(),
                            v.getChannelId(),
                            ApiEnums.Network.valueOf(v.getNetwork()),
                            PostContentMapper.mapToContent(v.getContent()),
                            PostContentMapper.mapToOptions(v.getOptions(), objectMapper)))
                    .toList();
            validateStoryForSchedule(post, upserts, PostContentMapper.mapToContent(post.getBaseContent()));
            PostDtos.StoryOptions story = StorySupport.toDto(
                    post.getStoryTtl(), post.getStoryLinkText(), post.getStoryLinkUrl(), post.getStoryOptions());
            applyStoryDeleteAfterToVariants(variants, story, request.scheduledAt());
        }
        ensureCanScheduleAd(membership, post);
        planLimitService.ensureCanCreatePost(post.getWorkspaceId());
        if (post.isAd()) {
            // Fail-closed before queue: creative must yield erid (idempotent by external_id).
            ordService.registerCreative(userId, postId, ctx);
            post = requirePost(postId);
            variants = postVariantRepository.findByPostId(postId);
        }

        Instant now = Instant.now();
        cancelPendingPublications(postId);
        post.setScheduledAt(request.scheduledAt());
        if (request.recurrence() != null) {
            post.setRecurrence(PostContentMapper.recurrenceToMap(request.recurrence(), objectMapper));
        }
        if (request.timezone() != null) {
            post.setTimezone(resolveTimezone(request.timezone()));
        }
        post.setStatus("scheduled");
        post.setUpdatedAt(now);
        materializePublications(post, variants, now);
        postRepository.save(post);
        writeOutbox(post, StorySupport.isStory(post.getContentKind()) ? "story.scheduled" : "post.scheduled");
        planLimitService.recordPostCreated(post.getWorkspaceId());
        auditRecorder.record(
                post.getWorkspaceId(),
                userId,
                StorySupport.isStory(post.getContentKind()) ? "story.scheduled" : "post.schedule",
                "post",
                postId,
                Map.of("scheduledAt", request.scheduledAt().toString()),
                ctx == null ? null : ctx.ip());
        return toDto(post, variants);
    }

    @Override
    public PostDtos.Post pause(UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        access.requirePermission(post.getWorkspaceId(), userId, "posts.bulk");
        assertVersion(post, request.version());
        if (!"scheduled".equals(post.getStatus()) && !"publishing".equals(post.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only scheduled posts can be paused");
        }
        cancelPendingPublications(postId);
        post.setStatus("paused");
        post.setUpdatedAt(Instant.now());
        postRepository.saveAndFlush(post);
        writeOutbox(post, "post.paused");
        return toDto(post, postVariantRepository.findByPostId(postId));
    }

    @Override
    public PostDtos.Post resume(
            UUID userId, UUID postId, PostDtos.PostActionRequest request, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        access.requirePermission(post.getWorkspaceId(), userId, "posts.bulk");
        assertVersion(post, request.version());
        if (!"paused".equals(post.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only paused posts can be resumed");
        }
        if (post.getScheduledAt() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, "paused post has no scheduledAt");
        }
        List<PostVariantEntity> variants = postVariantRepository.findByPostId(postId);
        Instant now = Instant.now();
        post.setStatus("scheduled");
        post.setUpdatedAt(now);
        materializePublications(post, variants, now);
        postRepository.saveAndFlush(post);
        writeOutbox(post, "post.resumed");
        return toDto(post, variants);
    }

    @Override
    public PostDtos.Post duplicate(UUID userId, UUID postId, ClientRequestContext ctx) {
        PostEntity source = requirePost(postId);
        access.requirePermission(source.getWorkspaceId(), userId, "posts.create");
        List<PostVariantEntity> sourceVariants = postVariantRepository.findByPostId(postId);
        Instant now = Instant.now();
        PostEntity copy = new PostEntity();
        copy.setId(UuidV7.generate());
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setProjectId(source.getProjectId());
        copy.setAuthorId(userId);
        copy.setTitle(source.getTitle() == null ? "Копия" : source.getTitle() + " (копия)");
        copy.setBaseContent(source.getBaseContent() == null ? Map.of() : Map.copyOf(source.getBaseContent()));
        copy.setStatus("draft");
        copy.setScheduledAt(null);
        copy.setRecurrence(source.getRecurrence());
        copy.setAd(source.isAd());
        copy.setAdPayload(source.getAdPayload());
        copy.setStopAt(source.getStopAt());
        copy.setRemoveAfterPublish(source.isRemoveAfterPublish());
        copy.setContentKind(source.getContentKind());
        copy.setSourceType("manual");
        copy.setTimezone(source.getTimezone());
        copy.setStoryTtl(source.getStoryTtl());
        copy.setStoryLinkText(source.getStoryLinkText());
        copy.setStoryLinkUrl(source.getStoryLinkUrl());
        copy.setStoryOptions(source.getStoryOptions());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        postRepository.save(copy);
        List<PostVariantEntity> copies = new ArrayList<>();
        for (PostVariantEntity v : sourceVariants) {
            PostVariantEntity nv = new PostVariantEntity();
            nv.setId(UuidV7.generate());
            nv.setPostId(copy.getId());
            nv.setChannelId(v.getChannelId());
            nv.setNetwork(v.getNetwork());
            nv.setContent(v.getContent() == null ? Map.of() : Map.copyOf(v.getContent()));
            nv.setOptions(v.getOptions() == null ? Map.of() : Map.copyOf(v.getOptions()));
            nv.setCreatedAt(now);
            nv.setUpdatedAt(now);
            copies.add(postVariantRepository.save(nv));
        }
        auditRecorder.record(
                copy.getWorkspaceId(),
                userId,
                "post.duplicate",
                "post",
                copy.getId(),
                Map.of("sourcePostId", postId.toString()),
                ctx == null ? null : ctx.ip());
        return toDto(copy, copies);
    }

    @Override
    public PostDtos.BulkPostsResponse bulk(UUID userId, PostDtos.BulkPostsRequest request, ClientRequestContext ctx) {
        if (request.postIds().isEmpty()) {
            return new PostDtos.BulkPostsResponse(0, List.of());
        }
        // Resolve workspace from first visible post
        PostEntity first = requirePost(request.postIds().getFirst());
        String action = request.action().trim().toLowerCase(Locale.ROOT);
        String permission =
                switch (action) {
                    case "delete" -> "posts.delete";
                    case "duplicate" -> "posts.create";
                    case "reschedule" -> "posts.schedule";
                    default -> "posts.bulk";
                };
        access.requirePermission(first.getWorkspaceId(), userId, permission);

        List<UUID> failed = new ArrayList<>();
        int affected = 0;
        for (UUID id : request.postIds()) {
            try {
                PostEntity post = requirePost(id);
                if (!post.getWorkspaceId().equals(first.getWorkspaceId())) {
                    failed.add(id);
                    continue;
                }
                switch (action) {
                    case "pause" -> pause(userId, id, new PostDtos.PostActionRequest(post.getVersion()), ctx);
                    case "resume" -> resume(userId, id, new PostDtos.PostActionRequest(post.getVersion()), ctx);
                    case "delete" -> delete(userId, id, ctx);
                    case "reschedule" -> {
                        if (request.scheduledAt() == null) {
                            failed.add(id);
                            continue;
                        }
                        schedule(
                                userId,
                                id,
                                new PostDtos.SchedulePostRequest(
                                        request.scheduledAt(), null, post.getTimezone(), post.getVersion()),
                                ctx);
                    }
                    case "duplicate" -> duplicate(userId, id, ctx);
                    default -> {
                        failed.add(id);
                        continue;
                    }
                }
                affected++;
            } catch (RuntimeException ex) {
                failed.add(id);
            }
        }
        return new PostDtos.BulkPostsResponse(affected, List.copyOf(failed));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDtos.PreviewResponse preview(UUID userId, PostDtos.PreviewRequest request) {
        // Preview is workspace-agnostic content render; still requires auth.
        AdMarkingValidator.validateNetworkOptions(request.network(), request.options());
        NetworkTextLimits limits = limitsFor(request.network());
        boolean hasMedia = !PostContentMapper.mediaIds(request.content()).isEmpty();
        String text = PostContentMapper.extractText(request.content());
        if (request.ad() && (request.erid() != null || request.adMarking() != null)) {
            OrdDtos.AdMarking marking = request.adMarking();
            String name = marking == null ? "" : nullToEmpty(marking.advertiserName());
            String inn = marking == null ? "" : nullToEmpty(marking.inn());
            String erid =
                    request.erid() != null ? request.erid() : (marking == null ? "" : nullToEmpty(marking.erid()));
            String marker = AdMarker.format(name, inn, erid);
            text = AdMarker.prepend(text, marker);
        }
        ContentRenderResult rendered = contentRenderer.render(
                new ContentRenderRequest(request.network().name(), text, hasMedia, limits));
        List<PostDtos.ValidationIssue> issues = rendered.issues().stream()
                .map(i -> new PostDtos.ValidationIssue(i.code(), i.severity(), i.message(), i.field()))
                .toList();
        String plain = rendered.plainText();
        String html = rendered.html();
        if (request.ad() && (request.erid() != null || request.adMarking() != null)) {
            // Ensure marker is visible even if renderer normalized whitespace.
            OrdDtos.AdMarking marking = request.adMarking();
            String name = marking == null ? "" : nullToEmpty(marking.advertiserName());
            String inn = marking == null ? "" : nullToEmpty(marking.inn());
            String erid =
                    request.erid() != null ? request.erid() : (marking == null ? "" : nullToEmpty(marking.erid()));
            String marker = AdMarker.format(name, inn, erid);
            if (plain == null || !plain.contains("erid:")) {
                plain = AdMarker.prepend(plain, marker);
            }
            if (html == null || !html.contains("erid:")) {
                html = AdMarker.prepend(html, marker);
            }
        }
        return new PostDtos.PreviewResponse(
                request.network(), html, plain, rendered.valid(), rendered.warnings(), issues);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.PublicationPage listPublications(UUID userId, UUID postId, CursorPage.Params page) {
        PostEntity post = requirePost(postId);
        access.requirePermission(post.getWorkspaceId(), userId, "posts.view");
        List<PublicationEntity> pubs = publicationRepository.findByPostId(postId).stream()
                .sorted(Comparator.comparing(PublicationEntity::getRunAt).reversed())
                .toList();
        int limit = page == null ? 20 : page.limitOrDefault();
        CursorPages.Slice<PublicationEntity> slice =
                CursorPages.slice(pubs, page == null ? null : page.cursor(), limit, PublicationEntity::getId);
        return new CursorPage.PublicationPage(
                slice.items().stream().map(this::toPublicationDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostDtos.CalendarItem> calendar(
            UUID userId,
            UUID workspaceId,
            Instant from,
            Instant to,
            UUID projectId,
            UUID channelId,
            UUID authorId,
            ApiEnums.Network network,
            ApiEnums.PostStatus status,
            PostDtos.ContentKind contentKind,
            String timezone) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "calendar.view");
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "to must be after from");
        }
        // timezone is accepted for FE day-boundary contract; from/to remain UTC instants.
        resolveTimezone(timezone);
        if (projectId != null) {
            access.requireVisibleProject(membership, projectId);
        }
        List<PostEntity> posts = new ArrayList<>(postRepository.search(
                workspaceId,
                from,
                to,
                status == null ? null : status.name(),
                projectId,
                authorId,
                channelId,
                network == null ? null : network.name(),
                null));
        posts = new ArrayList<>(filterVisibleProjects(membership, posts));
        if (contentKind != null) {
            posts = posts.stream()
                    .filter(p -> contentKind.name().equals(p.getContentKind() == null ? "post" : p.getContentKind()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        posts.sort(Comparator.comparing(PostEntity::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<UUID, List<PostVariantEntity>> variantsByPost = loadVariants(posts);
        List<PostDtos.CalendarItem> items = new ArrayList<>(posts.size());
        for (PostEntity post : posts) {
            if (post.getScheduledAt() == null) {
                continue;
            }
            List<PostVariantEntity> variants = variantsByPost.getOrDefault(post.getId(), List.of());
            items.add(toCalendarItem(post, variants));
        }
        return List.copyOf(items);
    }

    @Override
    public PostDtos.Post moveCalendar(
            UUID userId, UUID postId, PostDtos.MoveCalendarItemRequest request, ClientRequestContext ctx) {
        PostEntity post = requirePost(postId);
        access.requirePermission(post.getWorkspaceId(), userId, "calendar.move");
        assertVersion(post, request.version());
        return schedule(
                userId,
                postId,
                new PostDtos.SchedulePostRequest(
                        request.scheduledAt(),
                        PostContentMapper.mapToRecurrence(post.getRecurrence(), objectMapper),
                        request.timezone() == null ? post.getTimezone() : request.timezone(),
                        request.version()),
                ctx);
    }

    // --- helpers ---

    private void materializePublications(PostEntity post, List<PostVariantEntity> variants, Instant now) {
        PostDtos.Recurrence recurrence = PostContentMapper.mapToRecurrence(post.getRecurrence(), objectMapper);
        List<Instant> runAts =
                RecurrenceMaterializer.materialize(post.getScheduledAt(), recurrence, post.getTimezone(), now);
        Instant stopAt = post.getStopAt();
        List<PublicationEntity> existing = publicationRepository.findByPostId(post.getId());
        for (PostVariantEntity variant : variants) {
            for (Instant runAt : runAts) {
                if (stopAt != null && !runAt.isBefore(stopAt)) {
                    continue;
                }
                String key = publicationIdempotencyKey(variant.getId(), runAt, post.getContentKind());
                // Match by idempotency key OR (variantId + run_at): Instant nanos may drift after
                // timestamptz round-trip, so key-only matching misses cancelled/succeeded slots and
                // UNIQUE(idempotency_key, run_at) can still fire when run_at truncates equally.
                PublicationEntity matched = existing.stream()
                        .filter(p -> variant.getId().equals(p.getVariantId()))
                        .filter(p -> key.equals(p.getIdempotencyKey()) || sameRunAt(p.getRunAt(), runAt))
                        .findFirst()
                        .orElse(null);
                if (matched != null) {
                    if ("cancelled".equals(matched.getState())) {
                        matched.setState("pending");
                        matched.setErrorCode(null);
                        matched.setErrorMessage(null);
                        matched.setNextAttemptAt(null);
                        publicationRepository.save(matched);
                    }
                    // succeeded / pending / queued / in-flight — leave as-is (no re-insert).
                    continue;
                }
                ensurePublicationPartition(runAt);
                PublicationEntity pub = new PublicationEntity();
                pub.setId(UuidV7.generate());
                pub.setWorkspaceId(post.getWorkspaceId());
                pub.setPostId(post.getId());
                pub.setVariantId(variant.getId());
                pub.setChannelId(variant.getChannelId());
                pub.setNetwork(variant.getNetwork());
                pub.setRunAt(runAt);
                pub.setIdempotencyKey(key);
                pub.setState("pending");
                pub.setCreatedAt(now);
                publicationRepository.save(pub);
            }
        }
    }

    /** Compare run_at at microsecond precision (PostgreSQL timestamptz). */
    private static boolean sameRunAt(Instant left, Instant right) {
        if (left == null || right == null) {
            return false;
        }
        long leftMicros = left.getEpochSecond() * 1_000_000L + left.getNano() / 1_000;
        long rightMicros = right.getEpochSecond() * 1_000_000L + right.getNano() / 1_000;
        return leftMicros == rightMicros;
    }

    private void ensurePublicationPartition(Instant runAt) {
        // Partitions are pre-created for near months; out-of-range → clear error for operators.
        YearMonth ym = YearMonth.from(runAt.atZone(ZoneOffset.UTC));
        YearMonth min = YearMonth.of(2026, 10);
        YearMonth max = YearMonth.of(2027, 9);
        if (ym.isBefore(min) || ym.isAfter(max)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.VALIDATION_FAILED,
                    "scheduledAt is outside available publication partitions (" + min + ".." + max + ")");
        }
    }

    private void cancelPendingPublications(UUID postId) {
        List<PublicationEntity> pubs = publicationRepository.findByPostId(postId);
        for (PublicationEntity pub : pubs) {
            if ("pending".equals(pub.getState())
                    || "queued".equals(pub.getState())
                    || "failed_retryable".equals(pub.getState())
                    || "waiting_for_channel".equals(pub.getState())) {
                pub.setState("cancelled");
                publicationRepository.save(pub);
            }
        }
    }

    private List<PostVariantEntity> saveVariants(UUID postId, List<PostDtos.PostVariantUpsert> variants) {
        Instant now = Instant.now();
        List<PostVariantEntity> saved = new ArrayList<>();
        for (PostDtos.PostVariantUpsert upsert : variants) {
            PostVariantEntity entity = new PostVariantEntity();
            entity.setId(upsert.id() == null ? UuidV7.generate() : upsert.id());
            entity.setPostId(postId);
            entity.setChannelId(upsert.channelId());
            entity.setNetwork(upsert.network().name());
            entity.setContent(
                    upsert.content() == null
                            ? Map.of()
                            : PostContentMapper.contentToMap(upsert.content(), objectMapper));
            entity.setOptions(PostContentMapper.optionsToMap(upsert.options(), objectMapper));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            saved.add(postVariantRepository.save(entity));
        }
        return saved;
    }

    private void deleteVariants(UUID postId) {
        for (PostVariantEntity v : postVariantRepository.findByPostId(postId)) {
            postVariantRepository.delete(v);
        }
    }

    private void validateVariants(UUID workspaceId, List<PostDtos.PostVariantUpsert> variants) {
        if (variants == null) {
            return;
        }
        for (PostDtos.PostVariantUpsert v : variants) {
            ChannelEntity channel = channelRepository
                    .findByIdAndDeletedAtIsNull(v.channelId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
            if (!channel.getWorkspaceId().equals(workspaceId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found");
            }
            if (!channel.getNetwork().equals(v.network().name())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.VALIDATION_FAILED,
                        "Variant network does not match channel network");
            }
        }
    }

    private void validateMedia(UUID workspaceId, List<UUID> mediaIds) {
        for (UUID mediaId : mediaIds) {
            MediaAssetEntity asset = mediaAssetRepository
                    .findByWorkspaceIdAndIdAndDeletedAtIsNull(workspaceId, mediaId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ProblemCodes.VALIDATION_FAILED,
                            "Media asset not found in workspace: " + mediaId));
            if (!"ready".equals(asset.getStatus()) && !"processing".equals(asset.getStatus())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.VALIDATION_FAILED,
                        "Media asset is not ready: " + mediaId);
            }
        }
    }

    private void validateContentAgainstNetworks(PostDtos.PostContent base, List<PostDtos.PostVariantUpsert> variants) {
        for (PostDtos.PostVariantUpsert v : variants) {
            PostDtos.PostContent content = v.content() == null ? base : v.content();
            String text = PostContentMapper.extractText(content);
            if (text == null || text.isBlank()) {
                if (v.content() != null) {
                    text = PostContentMapper.extractText(base);
                }
            }
            boolean hasMedia = !PostContentMapper.mediaIds(content).isEmpty()
                    || !PostContentMapper.mediaIds(base).isEmpty();
            ContentRenderResult result = contentRenderer.render(new ContentRenderRequest(
                    v.network().name(), text == null ? "" : text, hasMedia, limitsFor(v.network())));
            if (!result.valid()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.VALIDATION_FAILED,
                        "Content validation failed for network " + v.network(),
                        Map.of(
                                "network",
                                v.network().name(),
                                "issues",
                                result.issues().stream()
                                        .map(i -> Map.of("code", i.code(), "message", i.message()))
                                        .toList()));
            }
        }
    }

    private NetworkTextLimits limitsFor(ApiEnums.Network network) {
        NetworkDtos.NetworkCapabilitiesResponse caps = networkCapabilitiesService.get();
        for (NetworkDtos.NetworkCapability cap : caps.networks()) {
            if (cap.id() == network && cap.text() != null) {
                Integer max = cap.text().maxChars();
                Integer caption = cap.text().captionMaxChars();
                // Fallbacks used by formatting fixtures when official max is unknown
                if (max == null) {
                    max = switch (network) {
                        case tg -> 4096;
                        case max -> 4000;
                        case vk, ok, rutube -> 16384;
                    };
                }
                if (caption == null && network == ApiEnums.Network.tg) {
                    caption = 1024;
                }
                return new NetworkTextLimits(max, caption);
            }
        }
        return new NetworkTextLimits(4096, 1024);
    }

    private void writeOutbox(PostEntity post, String eventType) {
        OutboxEntity event = new OutboxEntity();
        event.setAggregate("post");
        event.setAggregateId(post.getId());
        event.setEventType(eventType);
        event.setPayload(Map.of(
                "workspaceId", post.getWorkspaceId().toString(),
                "postId", post.getId().toString(),
                "status", post.getStatus()));
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);
    }

    private PostEntity requirePost(UUID postId) {
        return postRepository
                .findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Post not found"));
    }

    private void ensureProjectVisible(WorkspaceMembership membership, PostEntity post) {
        if (post.getProjectId() != null) {
            access.requireVisibleProject(membership, post.getProjectId());
        }
    }

    private List<PostEntity> filterVisibleProjects(WorkspaceMembership membership, List<PostEntity> posts) {
        if (membership.role().seesAllProjects()) {
            return posts;
        }
        return posts.stream()
                .filter(p -> p.getProjectId() == null
                        || membership.assignedProjectIds().contains(p.getProjectId()))
                .toList();
    }

    private Map<UUID, List<PostVariantEntity>> loadVariants(List<PostEntity> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = posts.stream().map(PostEntity::getId).toList();
        return postVariantRepository.findByPostIdIn(ids).stream()
                .collect(Collectors.groupingBy(PostVariantEntity::getPostId));
    }

    private void sortPosts(List<PostEntity> posts, PostDtos.PostSort sort) {
        PostDtos.PostSort effective = sort == null ? PostDtos.PostSort.scheduledAt_desc : sort;
        Comparator<PostEntity> cmp =
                switch (effective) {
                    case scheduledAt_asc -> Comparator.comparing(
                            PostEntity::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()));
                    case scheduledAt_desc -> Comparator.comparing(
                                    PostEntity::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed();
                    case title_asc -> Comparator.comparing(
                            p -> p.getTitle() == null ? "" : p.getTitle(), String.CASE_INSENSITIVE_ORDER);
                    case updatedAt_desc -> Comparator.comparing(PostEntity::getUpdatedAt)
                            .reversed();
                };
        posts.sort(cmp.thenComparing(PostEntity::getId, Comparator.reverseOrder()));
    }

    private static void assertVersion(PostEntity post, Integer version) {
        if (version == null || version != post.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Post version mismatch",
                    Map.of("currentVersion", post.getVersion()));
        }
    }

    private PostDtos.Post toDto(PostEntity post, List<PostVariantEntity> variants) {
        PostDtos.PostContent base = PostContentMapper.mapToContent(post.getBaseContent());
        PostDtos.RepostTarget repost = PostContentMapper.extractRepost(post.getBaseContent(), objectMapper);
        List<PostDtos.PostVariant> variantDtos = variants.stream()
                .map(v -> new PostDtos.PostVariant(
                        v.getId(),
                        v.getChannelId(),
                        ApiEnums.Network.valueOf(v.getNetwork()),
                        PostContentMapper.mapToContent(v.getContent()),
                        PostContentMapper.mapToOptions(v.getOptions(), objectMapper)))
                .toList();
        PostDtos.AdMarkingSummary adMarking = null;
        if (post.isAd()) {
            String erid = null;
            String advertiser = null;
            if (post.getAdPayload() != null) {
                Object e = post.getAdPayload().get("erid");
                Object a = post.getAdPayload().get("advertiserName");
                if (a == null) {
                    a = post.getAdPayload().get("fio");
                }
                erid = e == null ? null : String.valueOf(e);
                advertiser = a == null ? null : String.valueOf(a);
            }
            adMarking = new PostDtos.AdMarkingSummary(true, erid, advertiser);
        }
        PostDtos.StoryOptions story = null;
        if (StorySupport.isStory(post.getContentKind())) {
            story = StorySupport.toDto(
                    post.getStoryTtl(), post.getStoryLinkText(), post.getStoryLinkUrl(), post.getStoryOptions());
        }
        return new PostDtos.Post(
                post.getId(),
                post.getWorkspaceId(),
                post.getProjectId(),
                post.getAuthorId(),
                post.getTitle(),
                base,
                ApiEnums.PostStatus.valueOf(post.getStatus()),
                post.getScheduledAt(),
                PostContentMapper.mapToRecurrence(post.getRecurrence(), objectMapper),
                repost,
                post.isAd(),
                adMarking,
                PostDtos.ContentKind.valueOf(post.getContentKind() == null ? "post" : post.getContentKind()),
                story,
                post.getSourceType() == null ? "manual" : post.getSourceType(),
                post.getSourceRef(),
                post.getStopAt(),
                post.isRemoveAfterPublish(),
                post.getTimezone(),
                post.getVersion(),
                variantDtos,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private PostDtos.CalendarItem toCalendarItem(PostEntity post, List<PostVariantEntity> variants) {
        List<ApiEnums.Network> networks = variants.stream()
                .map(v -> ApiEnums.Network.valueOf(v.getNetwork()))
                .distinct()
                .toList();
        List<UUID> channelIds = variants.stream()
                .map(PostVariantEntity::getChannelId)
                .distinct()
                .toList();
        PostDtos.AdMarkingSummary adMarking = post.isAd() ? new PostDtos.AdMarkingSummary(true, null, null) : null;
        return new PostDtos.CalendarItem(
                post.getId(),
                post.getTitle(),
                ApiEnums.PostStatus.valueOf(post.getStatus()),
                post.getScheduledAt(),
                networks,
                post.getProjectId(),
                channelIds,
                PostDtos.ContentKind.valueOf(post.getContentKind() == null ? "post" : post.getContentKind()),
                post.isAd(),
                adMarking,
                post.getAuthorId());
    }

    private PostDtos.Publication toPublicationDto(PublicationEntity pub) {
        return new PostDtos.Publication(
                pub.getId(),
                pub.getWorkspaceId(),
                pub.getPostId(),
                pub.getVariantId(),
                pub.getChannelId(),
                ApiEnums.Network.valueOf(pub.getNetwork()),
                pub.getRunAt(),
                ApiEnums.PublicationState.valueOf(pub.getState()),
                pub.getAttempts(),
                pub.getNextAttemptAt(),
                pub.getRemotePostId(),
                pub.getRemoteUrl(),
                pub.getErrorCode(),
                pub.getErrorMessage(),
                pub.getPublishedAt(),
                pub.getCreatedAt());
    }

    private static String publicationIdempotencyKey(UUID variantId, Instant runAt, String contentKind) {
        String raw = variantId + "|" + runAt + "|" + (contentKind == null ? "post" : contentKind);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Europe/Moscow";
        }
        try {
            java.time.ZoneId.of(timezone.trim());
            return timezone.trim();
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Invalid timezone: " + timezone);
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostDtos.BatchStoriesResponse batchStories(
            UUID userId, PostDtos.BatchStoriesRequest request, String idempotencyKey, ClientRequestContext ctx) {
        int max = 50;
        if (request.mediaIds().size() > max || request.channelIds().size() > max) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_BATCH_LIMIT,
                    "Batch stories limited to " + max + " media items");
        }
        access.requirePermission(request.workspaceId(), userId, "posts.bulk");
        access.requirePermission(request.workspaceId(), userId, "posts.schedule");
        List<UUID> created = new ArrayList<>();
        List<PostDtos.BatchStoryFailure> failures = new ArrayList<>();
        Instant base = request.scheduledAt();
        int step = request.stepMinutes() == null ? 0 : request.stepMinutes();
        for (int i = 0; i < request.mediaIds().size(); i++) {
            UUID mediaId = request.mediaIds().get(i);
            Instant scheduledAt = base.plusSeconds(step * 60L * i);
            try {
                List<PostDtos.PostVariantUpsert> variants = new ArrayList<>();
                for (UUID channelId : request.channelIds()) {
                    ChannelEntity channel = channelRepository
                            .findByIdAndDeletedAtIsNull(channelId)
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
                    variants.add(new PostDtos.PostVariantUpsert(
                            null,
                            channelId,
                            ApiEnums.Network.valueOf(channel.getNetwork()),
                            new PostDtos.PostContent(null, null, List.of(mediaId)),
                            null));
                }
                PostDtos.CreatePostRequest create = new PostDtos.CreatePostRequest(
                        request.workspaceId(),
                        request.projectId(),
                        "Story batch " + (i + 1),
                        new PostDtos.PostContent("", null, List.of(mediaId)),
                        scheduledAt,
                        null,
                        null,
                        request.ad(),
                        request.adMarking(),
                        null,
                        null,
                        request.story() == null ? null : request.story().deleteAfterPublish(),
                        request.timezone(),
                        PostDtos.ContentKind.story,
                        request.story(),
                        variants);
                PostDtos.Post post = create(userId, create, null, ctx);
                created.add(post.id());
            } catch (ApiException ex) {
                failures.add(new PostDtos.BatchStoryFailure(mediaId, ex.getCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failures.add(new PostDtos.BatchStoryFailure(mediaId, ProblemCodes.VALIDATION_FAILED, ex.getMessage()));
            }
        }
        auditRecorder.record(
                request.workspaceId(),
                userId,
                "story.batch_created",
                "post",
                null,
                Map.of("created", created.size(), "failed", failures.size()),
                ctx == null ? null : ctx.ip());
        return new PostDtos.BatchStoriesResponse(created.size(), created, failures);
    }

    private void applyStoryFields(
            PostEntity post,
            PostDtos.StoryOptions story,
            List<PostDtos.PostVariantUpsert> variants,
            PostDtos.PostContent baseContent,
            UUID workspaceId) {
        if (!StorySupport.isStory(post.getContentKind())) {
            return;
        }
        List<ApiEnums.Network> networks = variants == null
                ? List.of()
                : variants.stream().map(PostDtos.PostVariantUpsert::network).toList();
        StorySupport.assertSupportedNetworks(variants);
        StorySupport.assertLink(story, networks);
        ApiEnums.StoryTtl ttl = StorySupport.resolveTtl(story);
        post.setStoryTtl(ttl.name());
        if (story != null) {
            post.setStoryLinkText(blankToNull(story.linkText()));
            post.setStoryLinkUrl(blankToNull(story.linkUrl()));
            if (Boolean.TRUE.equals(story.deleteAfterPublish())) {
                post.setRemoveAfterPublish(true);
            }
        }
        String mediaKind = "photo";
        List<String> mediaWarnings = List.of();
        List<UUID> mediaIds = PostContentMapper.mediaIds(baseContent);
        if (!mediaIds.isEmpty()) {
            MediaAssetEntity asset = mediaAssetRepository
                    .findByWorkspaceIdAndIdAndDeletedAtIsNull(workspaceId, mediaIds.getFirst())
                    .orElse(null);
            mediaKind = StorySupport.mediaKindOf(asset);
            if (asset != null) {
                mediaWarnings = StorySupport.assertMedia(baseContent, List.of(asset));
            }
        }
        post.setStoryOptions(StorySupport.toStoryOptionsMap(story, networks, mediaKind, mediaWarnings));
    }

    private void validateStoryForSchedule(
            PostEntity post, List<PostDtos.PostVariantUpsert> variants, PostDtos.PostContent baseContent) {
        StorySupport.assertSupportedNetworks(variants);
        List<ChannelEntity> channels = new ArrayList<>();
        for (PostDtos.PostVariantUpsert v : variants) {
            ChannelEntity channel = channelRepository
                    .findByIdAndDeletedAtIsNull(v.channelId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
            channels.add(channel);
        }
        StorySupport.assertChannelsReady(channels);
        List<UUID> mediaIds = PostContentMapper.mediaIds(baseContent);
        List<MediaAssetEntity> assets = new ArrayList<>();
        for (UUID mediaId : mediaIds) {
            mediaAssetRepository
                    .findByWorkspaceIdAndIdAndDeletedAtIsNull(post.getWorkspaceId(), mediaId)
                    .ifPresent(assets::add);
        }
        List<String> mediaWarnings = StorySupport.assertMedia(baseContent, assets);
        if (!mediaWarnings.isEmpty()) {
            Map<String, Object> opts =
                    new LinkedHashMap<>(post.getStoryOptions() == null ? Map.of() : post.getStoryOptions());
            @SuppressWarnings("unchecked")
            List<String> existing = opts.get("warnings") instanceof List<?> list
                    ? list.stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();
            for (String w : mediaWarnings) {
                if (!existing.contains(w)) {
                    existing.add(w);
                }
            }
            opts.put("warnings", existing);
            post.setStoryOptions(opts);
        }
        List<ApiEnums.Network> networks =
                variants.stream().map(PostDtos.PostVariantUpsert::network).toList();
        StorySupport.assertLink(
                StorySupport.toDto(
                        post.getStoryTtl(), post.getStoryLinkText(), post.getStoryLinkUrl(), post.getStoryOptions()),
                networks);
    }

    private List<PostDtos.PostVariantUpsert> enrichVariantsForStory(
            PostEntity post, List<PostDtos.PostVariantUpsert> variants, PostDtos.StoryOptions story) {
        if (!StorySupport.isStory(post.getContentKind()) || variants == null) {
            return variants;
        }
        Instant deleteAfter = StorySupport.resolveAutoDeleteAt(story, post.getScheduledAt());
        if (deleteAfter == null) {
            return variants;
        }
        List<PostDtos.PostVariantUpsert> enriched = new ArrayList<>(variants.size());
        for (PostDtos.PostVariantUpsert v : variants) {
            PostDtos.PostVariantOptions options = v.options();
            if (options == null) {
                options = new PostDtos.PostVariantOptions(
                        null,
                        null,
                        deleteAfter,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
            } else if (options.deleteAfter() == null) {
                options = new PostDtos.PostVariantOptions(
                        options.pin(),
                        options.unpinAt(),
                        deleteAfter,
                        options.mute(),
                        options.closeComments(),
                        options.signed(),
                        options.firstComment(),
                        options.buttons(),
                        options.carouselMode(),
                        options.donutDays(),
                        options.coOwnerIds(),
                        options.primaryAttachmentsMode(),
                        options.poll(),
                        options.geo(),
                        options.copyrightUrl(),
                        options.actionButton(),
                        options.extra());
            }
            enriched.add(new PostDtos.PostVariantUpsert(v.id(), v.channelId(), v.network(), v.content(), options));
        }
        return enriched;
    }

    private void applyStoryDeleteAfterToVariants(
            List<PostVariantEntity> variants, PostDtos.StoryOptions story, Instant scheduledAt) {
        Instant deleteAfter = StorySupport.resolveAutoDeleteAt(story, scheduledAt);
        if (deleteAfter == null || variants == null) {
            return;
        }
        for (PostVariantEntity variant : variants) {
            Map<String, Object> options =
                    new LinkedHashMap<>(variant.getOptions() == null ? Map.of() : variant.getOptions());
            if (!options.containsKey("deleteAfter")) {
                options.put("deleteAfter", deleteAfter.toString());
                variant.setOptions(options);
                variant.setUpdatedAt(Instant.now());
                postVariantRepository.save(variant);
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void ensureCanScheduleAd(WorkspaceMembership membership, PostEntity post) {
        if (!post.isAd()) {
            return;
        }
        if (membership.role() == RbacRole.author || membership.role() == RbacRole.viewer) {
            throw new ApiException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Authors cannot schedule ad posts");
        }
        access.requireAction(
                membership, "posts.ad.schedule", ru.posts.security.rbac.RbacEvaluationContext.workspaceOnly());
        if (findActiveOrdConnection(post.getWorkspaceId()).isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_NOT_CONFIGURED,
                    "Active ORD connection required to schedule ad posts");
        }
        if (post.getAdPayload() != null && !post.getAdPayload().isEmpty()) {
            OrdDtos.AdMarking marking = objectMapper.convertValue(post.getAdPayload(), OrdDtos.AdMarking.class);
            AdMarkingValidator.requireValidWhenAd(true, marking);
        } else {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "Ad marking is required when ad=true");
        }
    }

    private java.util.Optional<OrdConnectionEntity> findActiveOrdConnection(UUID workspaceId) {
        List<OrdConnectionEntity> all = ordConnectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId);
        return all.stream()
                .filter(c -> "active".equals(c.getStatus()) && c.isDefault())
                .findFirst()
                .or(() ->
                        all.stream().filter(c -> "active".equals(c.getStatus())).findFirst());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveAdPayload(boolean ad, OrdDtos.AdMarking adMarking, Map<String, Object> raw) {
        if (adMarking != null) {
            return objectMapper.convertValue(adMarking, Map.class);
        }
        if (raw != null) {
            return raw;
        }
        return ad ? Map.of() : null;
    }
}
