package ru.posts.grabber.ai;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.publish.GrabberSettingNotifier;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;

/**
 * Rewrites grabber text before transform. A failed rewrite, an unknown model, a disabled provider
 * or an empty balance keeps the original text and records one notification.
 */
@Service
@Profile("!openapi")
public class GrabberAiRewriteService {

    private static final Logger log = LoggerFactory.getLogger(GrabberAiRewriteService.class);

    private final GrabberProperties properties;
    private final ObjectProvider<GrabberRewriteProvider> providers;
    private final WalletLedgerJdbc ledger;
    private final GrabberSettingNotifier notifier;

    public GrabberAiRewriteService(
            GrabberProperties properties,
            ObjectProvider<GrabberRewriteProvider> providers,
            WalletLedgerJdbc ledger,
            GrabberSettingNotifier notifier) {
        this.properties = properties;
        this.providers = providers;
        this.ledger = ledger;
        this.notifier = notifier;
    }

    public record Attempt(boolean applied, String text, long costKopecks, String model) {
        public static Attempt unchanged(String text) {
            return new Attempt(false, text == null ? "" : text, 0L, "");
        }
    }

    public Attempt prepare(AutomationRuleEntity rule, GrabberItemEntity item, String sourceText, UUID userId) {
        String original = sourceText == null ? "" : sourceText;
        if (rule == null || !GrabberConfigSupport.aiRewriteEnabled(rule.getConfig())) {
            return Attempt.unchanged(original);
        }
        GrabberRewriteProvider provider = providers.getIfAvailable();
        if (!properties.getAi().isEnabled() || provider == null || !provider.enabled()) {
            notify(rule, userId, "disabled", "Нейросеть выключена. Пост опубликован без переписывания.");
            return Attempt.unchanged(original);
        }
        String model = GrabberConfigSupport.aiModel(rule.getConfig());
        if (!provider.models().contains(model)) {
            notify(
                    rule,
                    userId,
                    "unknown_model",
                    "Модель «" + model + "» недоступна. Пост опубликован без переписывания.");
            return Attempt.unchanged(original);
        }
        try {
            GrabberRewriteProvider.RewriteResult result = provider.rewrite(new GrabberRewriteProvider.RewriteRequest(
                    original, model, GrabberConfigSupport.aiCostKopecksOverride(rule.getConfig())));
            String rewritten = result.text() == null ? original : result.text();
            return new Attempt(true, rewritten, Math.max(0L, result.costKopecks()), result.model());
        } catch (RuntimeException ex) {
            log.warn(
                    "grabber AI rewrite failed rule={} item={}: {}",
                    rule.getId(),
                    item == null ? null : item.getId(),
                    ex.toString());
            notify(rule, userId, "provider_error", "Нейросеть не ответила. Пост опубликован без переписывания.");
            return Attempt.unchanged(original);
        }
    }

    /** Charges the wallet. Returns false when the balance is too low — caller keeps the original text. */
    public boolean charge(AutomationRuleEntity rule, GrabberItemEntity item, Attempt attempt, UUID userId) {
        if (attempt == null || !attempt.applied() || attempt.costKopecks() <= 0 || item == null || rule == null) {
            return true;
        }
        try {
            ledger.debit(
                    rule.getWorkspaceId(),
                    attempt.costKopecks(),
                    "neural",
                    "neural:grabber:" + item.getId(),
                    item.getId(),
                    "Нейросеть: " + attempt.model());
            return true;
        } catch (WalletLedgerJdbc.InsufficientFundsException ex) {
            log.warn(
                    "grabber AI insufficient funds workspace={} item={} need={}",
                    rule.getWorkspaceId(),
                    item.getId(),
                    attempt.costKopecks());
            notify(
                    rule,
                    userId,
                    "insufficient_funds",
                    "Недостаточно средств для нейросети. Пост опубликован без переписывания.");
            return false;
        }
    }

    private void notify(AutomationRuleEntity rule, UUID userId, String code, String message) {
        UUID actor = userId != null ? userId : rule.getCreatedBy();
        notifier.notify(actor, rule.getWorkspaceId(), rule.getId(), "ai_rewrite", code, message);
    }
}
