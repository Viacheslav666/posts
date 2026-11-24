package ru.posts.grabber.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.notify.GrabberSettingReporter;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;

@ExtendWith(MockitoExtension.class)
class GrabberAiRewriteServiceTest {

    @Mock
    private ObjectProvider<GrabberRewriteProvider> providers;

    @Mock
    private WalletLedgerJdbc ledger;

    @Mock
    private GrabberSettingReporter settingReporter;

    private GrabberProperties properties;
    private GrabberAiRewriteService service;
    private AutomationRuleEntity rule;
    private GrabberItemEntity item;

    @BeforeEach
    void setUp() {
        properties = new GrabberProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setCostKopecks(100);
        service = new GrabberAiRewriteService(properties, providers, ledger, settingReporter);
        rule = new AutomationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setWorkspaceId(UUID.randomUUID());
        rule.setCreatedBy(UUID.randomUUID());
        Map<String, Object> config = new HashMap<>();
        config.put("ai_rewrite_enabled", true);
        config.put("transform", Map.of("ai_model", "stub"));
        rule.setConfig(config);
        item = new GrabberItemEntity();
        item.setId(UUID.randomUUID());
        when(providers.getIfAvailable()).thenReturn(new StubGrabberRewriteProvider(properties));
    }

    @Test
    void stubRewritesAndDebitsNeural() {
        GrabberAiRewriteService.Attempt attempt = service.prepare(rule, item, "исходный", rule.getCreatedBy());
        assertThat(attempt.applied()).isTrue();
        assertThat(attempt.text()).isEqualTo("[ai:stub] исходный");
        assertThat(attempt.costKopecks()).isEqualTo(100);

        when(ledger.tryDebit(any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(WalletLedgerJdbc.DebitOutcome.charged(
                        new WalletLedgerJdbc.LedgerResult(UUID.randomUUID(), -100L, false)));
        assertThat(service.charge(rule, item, attempt, rule.getCreatedBy())).isTrue();
        verify(ledger)
                .tryDebit(
                        eq(rule.getWorkspaceId()),
                        eq(100L),
                        eq("neural"),
                        eq("neural:grabber:" + item.getId()),
                        eq(item.getId()),
                        eq("Нейросеть: stub"));
        verify(ledger, never()).debit(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void clientPriceDoesNotChangeTheCharge() {
        Map<String, Object> transform = new HashMap<>();
        transform.put("ai_model", "stub");
        transform.put("ai_cost_kopecks", 1L);
        rule.getConfig().put("transform", transform);

        GrabberAiRewriteService.Attempt attempt = service.prepare(rule, item, "исходный", rule.getCreatedBy());
        assertThat(attempt.costKopecks()).isEqualTo(100);
    }

    @Test
    void insufficientFundsKeepsOriginal() {
        when(ledger.tryDebit(any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(WalletLedgerJdbc.DebitOutcome.insufficient(rule.getWorkspaceId(), 0, 100));
        GrabberAiRewriteService.Attempt attempt = service.prepare(rule, item, "исходный", rule.getCreatedBy());
        assertThat(service.charge(rule, item, attempt, rule.getCreatedBy())).isFalse();
        verify(settingReporter).reportUserNotice(any(), eq(rule), eq("ai_rewrite"), eq("insufficient_funds"), any());
    }

    @Test
    void unknownModelDoesNotCharge() {
        rule.getConfig().put("transform", Map.of("ai_model", "gpt-x"));
        GrabberAiRewriteService.Attempt attempt = service.prepare(rule, item, "исходный", rule.getCreatedBy());
        assertThat(attempt.applied()).isFalse();
        assertThat(attempt.text()).isEqualTo("исходный");
        verify(ledger, never()).tryDebit(any(), anyLong(), any(), any(), any(), any());
        verify(settingReporter).reportUserNotice(any(), eq(rule), eq("ai_rewrite"), eq("unknown_model"), any());
    }

    @Test
    void disabledProviderKeepsOriginal() {
        properties.getAi().setEnabled(false);
        GrabberAiRewriteService.Attempt attempt = service.prepare(rule, item, "исходный", rule.getCreatedBy());
        assertThat(attempt.applied()).isFalse();
        verify(settingReporter).reportUserNotice(any(), eq(rule), eq("ai_rewrite"), eq("disabled"), any());
    }
}
