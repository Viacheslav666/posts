package ru.posts.suggest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.social.inbound.HandlerContext;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialEventHandler;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundEvent;

/** BE-34: consumes POST_SUGGESTED for VK (order=20). Always CONTINUE so crosspost can run. */
@Profile("!openapi")
@Component
public class SuggestEventHandler implements SocialEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SuggestEventHandler.class);

    private final SuggestProcessingService processingService;

    public SuggestEventHandler(SuggestProcessingService processingService) {
        this.processingService = processingService;
    }

    @Override
    public String consumerKey() {
        return "suggest";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(SocialEventKind kind, String network) {
        return kind == SocialEventKind.POST_SUGGESTED && network != null && "vk".equalsIgnoreCase(network);
    }

    @Override
    public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
        if (event == null || event.channelId() == null) {
            return HandlerResult.CONTINUE;
        }
        String vkPostId = null;
        if (event.remoteIds() != null) {
            vkPostId = event.remoteIds().get("post_id");
        }
        if (vkPostId == null || vkPostId.isBlank()) {
            log.debug("suggest skip: missing post_id channelId={}", event.channelId());
            return HandlerResult.CONTINUE;
        }
        String authorVkId = event.author() == null ? null : event.author().id();
        List<Map<String, Object>> attachments = toAttachments(event.attachments());
        Map<String, Object> raw = new LinkedHashMap<>();
        if (event.remoteIds() != null) {
            raw.put("remoteIds", event.remoteIds());
        }
        if (event.raw() != null) {
            raw.put("raw", event.raw());
        }
        try {
            processingService.processSuggested(
                    event.channelId(), vkPostId, authorVkId, event.text(), attachments, raw, null, null, null);
        } catch (RuntimeException ex) {
            log.warn(
                    "suggest processing failed channelId={} postId={}: {}",
                    event.channelId(),
                    vkPostId,
                    ex.getMessage());
        }
        return HandlerResult.CONTINUE;
    }

    private static List<Map<String, Object>> toAttachments(List<SocialInboundAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SocialInboundAttachment a : attachments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", a.type() == null ? "unknown" : a.type().toLowerCase(Locale.ROOT));
            if (a.remoteRef() != null) {
                map.put("remoteRef", a.remoteRef());
            }
            if (a.url() != null) {
                map.put("url", a.url());
            }
            out.add(map);
        }
        return out;
    }
}
