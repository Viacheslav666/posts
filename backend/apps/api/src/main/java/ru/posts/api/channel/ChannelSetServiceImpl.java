package ru.posts.api.channel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelSetEntity;
import ru.posts.persistence.entity.ChannelSetItemEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.ChannelSetItemRepository;
import ru.posts.persistence.repository.ChannelSetRepository;

@Service
@Profile("!openapi")
public class ChannelSetServiceImpl implements ChannelSetService {

    public static final int MAX_CHANNELS_PER_SET = 50;

    private final WorkspaceAccessService access;
    private final ChannelSetRepository channelSetRepository;
    private final ChannelSetItemRepository channelSetItemRepository;
    private final ChannelRepository channelRepository;
    private final AuditRecorder auditRecorder;

    public ChannelSetServiceImpl(
            WorkspaceAccessService access,
            ChannelSetRepository channelSetRepository,
            ChannelSetItemRepository channelSetItemRepository,
            ChannelRepository channelRepository,
            AuditRecorder auditRecorder) {
        this.access = access;
        this.channelSetRepository = channelSetRepository;
        this.channelSetItemRepository = channelSetItemRepository;
        this.channelRepository = channelRepository;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.ChannelSetPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "channel_sets.view");
        List<ChannelDtos.ChannelSet> items = channelSetRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toDto)
                .limit(page.limitOrDefault())
                .toList();
        return new CursorPage.ChannelSetPage(items, null, false);
    }

    @Override
    @Transactional
    public ChannelDtos.ChannelSet create(
            UUID userId, UUID workspaceId, ChannelDtos.CreateChannelSetRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "channel_sets.manage");
        List<UUID> channelIds = normalizeChannelIds(request.channelIds());
        validateMembership(workspaceId, channelIds);
        ChannelSetEntity set = new ChannelSetEntity();
        set.setId(UuidV7.generate());
        set.setWorkspaceId(workspaceId);
        set.setName(request.name());
        set.setCreatedAt(Instant.now());
        set.setUpdatedAt(Instant.now());
        channelSetRepository.save(set);
        replaceItems(set.getId(), channelIds);
        auditRecorder.record(
                workspaceId,
                userId,
                "channel_set.created",
                "channel_set",
                set.getId(),
                Map.of("size", channelIds.size()),
                ctx.ip());
        return toDto(set);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelDtos.ChannelSet get(UUID userId, UUID setId) {
        ChannelSetEntity set = requireSet(setId);
        access.requirePermission(set.getWorkspaceId(), userId, "channel_sets.view");
        return toDto(set);
    }

    @Override
    @Transactional
    public ChannelDtos.ChannelSet update(
            UUID userId, UUID setId, ChannelDtos.UpdateChannelSetRequest request, ClientRequestContext ctx) {
        ChannelSetEntity set = requireSet(setId);
        access.requirePermission(set.getWorkspaceId(), userId, "channel_sets.manage");
        if (request.name() != null && !request.name().isBlank()) {
            set.setName(request.name());
        }
        if (request.channelIds() != null) {
            List<UUID> channelIds = normalizeChannelIds(request.channelIds());
            validateMembership(set.getWorkspaceId(), channelIds);
            replaceItems(set.getId(), channelIds);
        }
        set.setUpdatedAt(Instant.now());
        channelSetRepository.save(set);
        auditRecorder.record(
                set.getWorkspaceId(), userId, "channel_set.updated", "channel_set", set.getId(), Map.of(), ctx.ip());
        return toDto(set);
    }

    @Override
    @Transactional
    public AuthDtos.MessageResponse delete(UUID userId, UUID setId, ClientRequestContext ctx) {
        ChannelSetEntity set = requireSet(setId);
        access.requirePermission(set.getWorkspaceId(), userId, "channel_sets.manage");
        channelSetItemRepository.deleteAll(channelSetItemRepository.findBySetId(setId));
        channelSetRepository.delete(set);
        auditRecorder.record(
                set.getWorkspaceId(), userId, "channel_set.deleted", "channel_set", setId, Map.of(), ctx.ip());
        return new AuthDtos.MessageResponse("Channel set deleted");
    }

    private List<UUID> normalizeChannelIds(List<UUID> channelIds) {
        Set<UUID> unique = new LinkedHashSet<>(channelIds);
        if (unique.size() > MAX_CHANNELS_PER_SET) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "Channel set may contain at most " + MAX_CHANNELS_PER_SET + " channels",
                    Map.of("limit", MAX_CHANNELS_PER_SET));
        }
        return new ArrayList<>(unique);
    }

    private void validateMembership(UUID workspaceId, List<UUID> channelIds) {
        for (UUID channelId : channelIds) {
            ChannelEntity channel = channelRepository
                    .findByIdAndDeletedAtIsNull(channelId)
                    .orElseThrow(WorkspaceAccessService::notFound);
            if (!workspaceId.equals(channel.getWorkspaceId())) {
                throw WorkspaceAccessService.notFound();
            }
        }
    }

    private void replaceItems(UUID setId, List<UUID> channelIds) {
        channelSetItemRepository.deleteAll(channelSetItemRepository.findBySetId(setId));
        for (UUID channelId : channelIds) {
            ChannelSetItemEntity item = new ChannelSetItemEntity();
            item.setSetId(setId);
            item.setChannelId(channelId);
            channelSetItemRepository.save(item);
        }
    }

    private ChannelSetEntity requireSet(UUID setId) {
        return channelSetRepository.findById(setId).orElseThrow(WorkspaceAccessService::notFound);
    }

    private ChannelDtos.ChannelSet toDto(ChannelSetEntity set) {
        List<UUID> channelIds = channelSetItemRepository.findBySetId(set.getId()).stream()
                .map(ChannelSetItemEntity::getChannelId)
                .toList();
        return new ChannelDtos.ChannelSet(set.getId(), set.getWorkspaceId(), set.getName(), channelIds);
    }
}
