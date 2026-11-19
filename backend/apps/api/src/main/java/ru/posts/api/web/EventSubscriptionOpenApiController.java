package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.EventSubscriptionDtos;

@RestController
@Profile("openapi")
@RequestMapping("/api/v1")
@Tag(name = "Event subscriptions", description = "Inbound social event subscription status / repair (BE-32)")
public class EventSubscriptionOpenApiController {

    @GetMapping("/channels/{channelId}/event-subscription")
    @Operation(operationId = "channelsEventSubscriptionGet", summary = "Get channel event subscription status")
    public EventSubscriptionDtos.EventSubscriptionResponse get(@PathVariable UUID channelId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/channels/{channelId}/event-subscription/repair")
    @Operation(operationId = "channelsEventSubscriptionRepair", summary = "Repair / re-register event subscription")
    public EventSubscriptionDtos.EventSubscriptionResponse repair(@PathVariable UUID channelId) {
        throw new NotImplementedException("openapi");
    }
}
