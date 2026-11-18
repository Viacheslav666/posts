package ru.posts.api.post;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.persistence.entity.PublicationAttemptEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.PublicationAttemptRepository;
import ru.posts.persistence.repository.PublicationRepository;

@Service
@Profile("!openapi")
@Transactional
public class PublicationServiceImpl implements PublicationService {

    private final PublicationRepository publicationRepository;
    private final PublicationAttemptRepository publicationAttemptRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;

    public PublicationServiceImpl(
            PublicationRepository publicationRepository,
            PublicationAttemptRepository publicationAttemptRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder) {
        this.publicationRepository = publicationRepository;
        this.publicationAttemptRepository = publicationAttemptRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.PublicationPage list(
            UUID userId,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            ApiEnums.PublicationState state,
            ApiEnums.Network network,
            CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "posts.view");
        List<PublicationEntity> all = publicationRepository.findByWorkspaceId(workspaceId).stream()
                .filter(p -> postId == null || postId.equals(p.getPostId()))
                .filter(p -> channelId == null || channelId.equals(p.getChannelId()))
                .filter(p -> state == null || state.name().equals(p.getState()))
                .filter(p -> network == null || network.name().equals(p.getNetwork()))
                .sorted(Comparator.comparing(PublicationEntity::getRunAt).reversed())
                .toList();
        int limit = page == null ? 20 : page.limitOrDefault();
        CursorPages.Slice<PublicationEntity> slice =
                CursorPages.slice(all, page == null ? null : page.cursor(), limit, PublicationEntity::getId);
        return new CursorPage.PublicationPage(
                slice.items().stream().map(this::toDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public PostDtos.Publication get(UUID userId, UUID publicationId) {
        PublicationEntity pub = requirePublication(publicationId);
        access.requirePermission(pub.getWorkspaceId(), userId, "posts.view");
        return toDto(pub);
    }

    @Override
    public PostDtos.Publication retry(UUID userId, UUID publicationId, ClientRequestContext ctx) {
        PublicationEntity pub = requirePublication(publicationId);
        access.requirePermission(pub.getWorkspaceId(), userId, "posts.retry_publish");
        if (!"failed_retryable".equals(pub.getState())
                && !"failed_permanent".equals(pub.getState())
                && !"waiting_for_channel".equals(pub.getState())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Publication is not in a retryable state");
        }
        pub.setState("pending");
        pub.setNextAttemptAt(Instant.now());
        pub.setErrorCode(null);
        pub.setErrorMessage(null);
        publicationRepository.save(pub);
        auditRecorder.record(
                pub.getWorkspaceId(),
                userId,
                "publication.retry",
                "publication",
                publicationId,
                Map.of("state", "pending"),
                ctx == null ? null : ctx.ip());
        return toDto(pub);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostDtos.PublicationAttempt> listAttempts(UUID userId, UUID publicationId) {
        PublicationEntity pub = requirePublication(publicationId);
        access.requirePermission(pub.getWorkspaceId(), userId, "posts.view");
        return publicationAttemptRepository.findByPublicationIdOrderByAttemptNoAsc(publicationId).stream()
                .map(this::toAttemptDto)
                .toList();
    }

    private PublicationEntity requirePublication(UUID publicationId) {
        return publicationRepository
                .findFirstByPublicationId(publicationId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Publication not found"));
    }

    private PostDtos.Publication toDto(PublicationEntity pub) {
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

    private PostDtos.PublicationAttempt toAttemptDto(PublicationAttemptEntity attempt) {
        return new PostDtos.PublicationAttempt(
                attempt.getId(),
                attempt.getPublicationId(),
                attempt.getAttemptNo(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getHttpStatus(),
                attempt.getErrorCode(),
                attempt.getResponseExcerpt());
    }
}
