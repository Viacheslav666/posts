package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.EventSubscriptionDtos;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelEventSubscriptionEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.security.auth.SecurityAuthSupport;
import ru.posts.social.inbound.subscription.EventSubscriptionService;

@RestController
@Profile("!openapi")
@RequestMapping("/api/v1")
@Tag(name = "Event subscriptions", description = "Inbound social event subscription status / repair (BE-32)")
public class EventSubscriptionController {

    private final ChannelRepository channelRepository;
    private final WorkspaceAccessService access;
    private final EventSubscriptionService subscriptionService;

    public EventSubscriptionController(
            ChannelRepository channelRepository,
            WorkspaceAccessService access,
            EventSubscriptionService subscriptionService) {
        this.channelRepository = channelRepository;
        this.access = access;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/channels/{channelId}/event-subscription")
    @Operation(operationId = "channelsEventSubscriptionGet", summary = "Get channel event subscription status")
    public EventSubscriptionDtos.EventSubscriptionResponse get(@PathVariable UUID channelId) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        ChannelEntity channel = requireChannel(channelId);
        access.requirePermission(channel.getWorkspaceId(), userId, "automations.view");
        ChannelEventSubscriptionEntity sub = subscriptionService
                .find(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
        return toDto(sub);
    }

    @PostMapping("/channels/{channelId}/event-subscription/repair")
    @Operation(operationId = "channelsEventSubscriptionRepair", summary = "Repair / re-register event subscription")
    public EventSubscriptionDtos.EventSubscriptionResponse repair(@PathVariable UUID channelId) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        ChannelEntity channel = requireChannel(channelId);
        access.requirePermission(channel.getWorkspaceId(), userId, "automations.manage");
        try {
            return toDto(subscriptionService.repair(channelId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Missing subscription / remote registration failure → 404 (IDOR-safe; RBAC matrix probe).
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, ex.getMessage());
        }
    }

    private ChannelEntity requireChannel(UUID channelId) {
        return channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
    }

    private static EventSubscriptionDtos.EventSubscriptionResponse toDto(ChannelEventSubscriptionEntity sub) {
        String warning = "tg_custom_bot".equals(sub.getMode()) ? "custom_bot_webhook_takeover" : null;
        return new EventSubscriptionDtos.EventSubscriptionResponse(
                sub.getId(),
                sub.getChannelId(),
                sub.getWorkspaceId(),
                sub.getNetwork(),
                sub.getMode(),
                sub.getStatus(),
                sub.getConsumers() == null ? List.of() : sub.getConsumers(),
                sub.getEventTypes() == null ? List.of() : sub.getEventTypes(),
                sub.getLastEventAt(),
                sub.getLastError(),
                sub.getFailureCount(),
                sub.getUpdatedAt() == null ? Instant.now() : sub.getUpdatedAt(),
                warning);
    }
}
