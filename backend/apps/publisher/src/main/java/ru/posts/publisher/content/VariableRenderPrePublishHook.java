package ru.posts.publisher.content;

import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.posts.content.VariableTemplateRenderer;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.publisher.extension.PrePublishHook;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.SocialAdapter;

/**
 * BE-29: expand editor variables and deterministic {@code [rand]} before ORD marker / network publish.
 *
 * <p>Spintax seed is the publication idempotency key so retries of the same publication never change
 * the chosen variant.
 */
@Component
@Order(50)
public class VariableRenderPrePublishHook implements PrePublishHook {

    private final SocialAdapter socialAdapter;

    public VariableRenderPrePublishHook(SocialAdapter socialAdapter) {
        this.socialAdapter = socialAdapter;
    }

    @Override
    public PostVariantContent beforePublish(PublicationClaim claim, PostVariantContent content) {
        if (claim == null || content == null) {
            return content;
        }
        String text = content.text();
        if (text == null || text.isBlank()) {
            return content;
        }
        String network = claim.network() == null ? "" : claim.network().toLowerCase(Locale.ROOT);
        boolean htmlNetwork = "tg".equals(network) || "telegram".equals(network) || "max".equals(network);

        String birthdays = "";
        if (VariableTemplateRenderer.needsBirthdays(text)) {
            ChannelContext ctx = new ChannelContext(
                    claim.workspaceId(),
                    claim.channelId(),
                    claim.externalChannelId(),
                    "emulator-token",
                    Network.fromCode(claim.network()));
            birthdays = socialAdapter.resolveBirthdays(ctx, claim.runAt(), claim.timezone());
        }

        String seed = claim.idempotencyKey() == null || claim.idempotencyKey().isBlank()
                ? String.valueOf(claim.id())
                : claim.idempotencyKey();
        String expanded = VariableTemplateRenderer.render(
                text,
                VariableTemplateRenderer.Context.builder()
                        .at(claim.runAt())
                        .zone(claim.timezone())
                        .groupId(claim.externalChannelId())
                        .groupName(claim.channelTitle())
                        .birthdays(birthdays)
                        .htmlNetwork(htmlNetwork)
                        .spintaxSeed(seed)
                        .build());
        return content.withText(expanded);
    }
}
