package ru.posts.api.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AutopaySettingsEntity;
import ru.posts.persistence.entity.InvoiceEntity;
import ru.posts.persistence.entity.PaymentEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PromoRedemptionEntity;
import ru.posts.persistence.entity.SubscriptionEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.AutopaySettingsRepository;
import ru.posts.persistence.repository.InvoiceRepository;
import ru.posts.persistence.repository.PaymentRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.PromoRedemptionRepository;
import ru.posts.persistence.repository.SubscriptionRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.yookassa.PaymentResponse;

@Service
@Profile("!openapi")
public class BillingPaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(BillingPaymentProcessor.class);

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final AutopaySettingsRepository autopaySettingsRepository;
    private final BillingFreezeService freezeService;
    private final WalletLedgerService walletLedgerService;
    private final BillingProperties billingProperties;

    public BillingPaymentProcessor(
            PaymentRepository paymentRepository,
            SubscriptionRepository subscriptionRepository,
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            InvoiceRepository invoiceRepository,
            PromoCodeRepository promoCodeRepository,
            PromoRedemptionRepository promoRedemptionRepository,
            AutopaySettingsRepository autopaySettingsRepository,
            BillingFreezeService freezeService,
            WalletLedgerService walletLedgerService,
            BillingProperties billingProperties) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.promoRedemptionRepository = promoRedemptionRepository;
        this.autopaySettingsRepository = autopaySettingsRepository;
        this.freezeService = freezeService;
        this.walletLedgerService = walletLedgerService;
        this.billingProperties = billingProperties;
    }

    @Transactional
    public void onPaymentSucceeded(PaymentResponse verified) {
        PaymentEntity payment = paymentRepository
                .findByProviderPaymentId(verified.id())
                .orElseGet(() -> createPaymentFromProvider(verified));
        if ("succeeded".equals(payment.getStatus())) {
            return;
        }

        WorkspaceEntity workspace =
                workspaceRepository.findByIdForUpdate(payment.getWorkspaceId()).orElse(null);
        if (workspace == null) {
            log.warn("Payment {} succeeded but workspace {} missing", payment.getId(), payment.getWorkspaceId());
            return;
        }

        payment = paymentRepository.findById(payment.getId()).orElse(payment);
        if ("succeeded".equals(payment.getStatus())) {
            return;
        }

        payment.setStatus("succeeded");
        payment.setUpdatedAt(Instant.now());
        if (payment.getAmountKopecks() <= 0 && payment.getAmount() != null) {
            payment.setAmountKopecks(MoneyKopecks.toKopecks(payment.getAmount()));
        }
        String paymentMethodId = null;
        String cardMask = null;
        if (verified.paymentMethod() != null && verified.paymentMethod().id() != null) {
            paymentMethodId = verified.paymentMethod().id();
            Map<String, Object> metadata =
                    new HashMap<>(payment.getMetadata() == null ? Map.of() : payment.getMetadata());
            metadata.put("payment_method_id", paymentMethodId);
            if (verified.paymentMethod().type() != null) {
                cardMask = verified.paymentMethod().type();
                metadata.put("card_mask", cardMask);
            }
            payment.setMetadata(metadata);
        }
        paymentRepository.save(payment);

        String purpose = payment.getPurpose() == null ? "subscription" : payment.getPurpose();
        boolean balanceTopup = "topup".equals(purpose) || "autopay".equals(purpose) || "invoice".equals(purpose);

        creditWallet(payment);

        if (paymentMethodId != null) {
            savePaymentMethod(workspace.getId(), paymentMethodId, cardMask, payment.getId());
        }

        if (balanceTopup || "balance".equalsIgnoreCase(workspace.getBillingModel())) {
            workspace.setBillingStatus("active");
            workspace.setUpdatedAt(Instant.now());
            workspaceRepository.save(workspace);
            freezeService.unfreeze(workspace.getId());
            redeemPromoIfNeeded(payment, workspace.getId());
            ensureInvoice(payment, balanceTopup ? "Balance top-up" : "Payment");
            return;
        }

        // Legacy subscription activation
        PlanEntity plan = resolvePlan(payment, verified);
        if (plan == null) {
            log.warn("Payment {} succeeded but plan unresolved", payment.getId());
            return;
        }

        Instant now = Instant.now();
        String billingPeriod = payment.getBillingPeriod() != null ? payment.getBillingPeriod() : "month";
        Instant periodEnd = anniversaryEnd(now, billingPeriod);

        SubscriptionEntity subscription = subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspace.getId())
                .orElseGet(SubscriptionEntity::new);
        if (subscription.getId() == null) {
            subscription.setId(UuidV7.generate());
            subscription.setWorkspaceId(workspace.getId());
            subscription.setCreatedAt(now);
        }
        subscription.setPlanId(plan.getId());
        subscription.setStatus("active");
        subscription.setPeriodStart(now);
        subscription.setPeriodEnd(periodEnd);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setBillingPeriod(billingPeriod);
        subscription.setGraceEndsAt(null);
        subscription.setFrozenAt(null);
        if (paymentMethodId != null) {
            subscription.setProviderPaymentMethodId(paymentMethodId);
        }
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        workspace.setPlanId(plan.getId());
        workspace.setBillingStatus("active");
        workspace.setCurrentPeriodEnd(periodEnd);
        workspace.setTrialEndsAt(null);
        workspace.setUpdatedAt(now);
        workspaceRepository.save(workspace);

        freezeService.unfreeze(workspace.getId());
        redeemPromoIfNeeded(payment, workspace.getId());
        ensureInvoice(payment, "Subscription payment");

        // Migration path: when flipping to balance model via feature flag, leftover period credited separately.
        if (billingProperties.isBalanceModel() && !"balance".equals(workspace.getBillingModel())) {
            workspace.setBillingModel("balance");
            workspaceRepository.save(workspace);
        }
    }

    @Transactional
    public void onPaymentCanceled(PaymentResponse verified) {
        paymentRepository.findByProviderPaymentId(verified.id()).ifPresent(payment -> {
            if ("succeeded".equals(payment.getStatus()) || "cancelled".equals(payment.getStatus())) {
                return;
            }
            payment.setStatus("cancelled");
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
        });
    }

    @Transactional
    public void onRefundSucceeded(String providerPaymentId, BigDecimal refundAmount) {
        PaymentEntity payment =
                paymentRepository.findByProviderPaymentId(providerPaymentId).orElse(null);
        if (payment == null) {
            return;
        }
        if ("refunded".equals(payment.getStatus())) {
            return;
        }
        payment.setStatus("refunded");
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);

        long kopecks = refundAmount != null ? MoneyKopecks.toKopecks(refundAmount) : payment.getAmountKopecks();
        var wallet = walletLedgerService.getOrCreate(payment.getWorkspaceId());
        long debit = Math.min(kopecks, Math.max(0L, wallet.balanceKopecks()));
        if (debit > 0) {
            walletLedgerService.debit(
                    payment.getWorkspaceId(),
                    debit,
                    "refund",
                    "refund:" + payment.getId(),
                    payment.getId(),
                    "YooKassa refund");
        }
    }

    private void creditWallet(PaymentEntity payment) {
        long kopecks = payment.getAmountKopecks() > 0
                ? payment.getAmountKopecks()
                : MoneyKopecks.toKopecks(payment.getAmount());
        if (kopecks <= 0) {
            return;
        }
        walletLedgerService.credit(
                payment.getWorkspaceId(),
                kopecks,
                "payment",
                false,
                "payment:" + payment.getId() + ":credit",
                payment.getId(),
                "YooKassa " + (payment.getPurpose() == null ? "payment" : payment.getPurpose()));
    }

    private void savePaymentMethod(UUID workspaceId, String paymentMethodId, String cardMask, UUID paymentId) {
        AutopaySettingsEntity settings = autopaySettingsRepository
                .findById(workspaceId)
                .orElseGet(() -> {
                    AutopaySettingsEntity created = new AutopaySettingsEntity();
                    created.setWorkspaceId(workspaceId);
                    created.setEnabled(false);
                    created.setThresholdKopecks(billingProperties.getAutopayDefaultThresholdKopecks());
                    created.setTopupAmountKopecks(billingProperties.getAutopayDefaultTopupKopecks());
                    return created;
                });
        settings.setProviderPaymentMethodId(paymentMethodId);
        if (cardMask != null) {
            settings.setCardMask(cardMask);
        }
        settings.setLastPaymentId(paymentId);
        settings.setUpdatedAt(Instant.now());
        autopaySettingsRepository.save(settings);
    }

    private PaymentEntity createPaymentFromProvider(PaymentResponse verified) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(UuidV7.generate());
        Map<String, String> meta = verified.metadata() == null ? Map.of() : verified.metadata();
        UUID workspaceId = parseUuid(meta.get("workspaceId"));
        if (workspaceId == null) {
            workspaceId = parseUuid(meta.get("workspace_id"));
        }
        if (workspaceId == null) {
            throw new IllegalStateException("YooKassa payment missing workspace metadata: " + verified.id());
        }
        payment.setWorkspaceId(workspaceId);
        payment.setProvider("yookassa");
        payment.setProviderPaymentId(verified.id());
        payment.setAmount(
                verified.amount() != null ? new BigDecimal(verified.amount().value()) : BigDecimal.ZERO);
        payment.setAmountKopecks(MoneyKopecks.toKopecks(payment.getAmount()));
        payment.setCurrency(
                verified.amount() != null && verified.amount().currency() != null
                        ? verified.amount().currency()
                        : "RUB");
        payment.setStatus("pending");
        payment.setPurpose(meta.getOrDefault("purpose", "subscription"));
        payment.setBillingPeriod(meta.getOrDefault("period", meta.get("billingPeriod")));
        payment.setPromoCode(meta.get("promoCode"));
        UUID planId = parseUuid(meta.get("planId"));
        if (planId != null) {
            payment.setPlanId(planId);
        } else {
            String planCode = meta.get("planCode");
            if (planCode != null) {
                planRepository.findByCode(planCode).ifPresent(p -> payment.setPlanId(p.getId()));
            }
        }
        payment.setMetadata(new HashMap<>(meta));
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    private PlanEntity resolvePlan(PaymentEntity payment, PaymentResponse verified) {
        if (payment.getPlanId() != null) {
            return planRepository.findById(payment.getPlanId()).orElse(null);
        }
        Map<String, String> meta = verified.metadata() == null ? Map.of() : verified.metadata();
        String planCode = meta.get("planCode");
        if (planCode != null) {
            return planRepository.findByCode(planCode).orElse(null);
        }
        return null;
    }

    private void redeemPromoIfNeeded(PaymentEntity payment, UUID workspaceId) {
        if (payment.getPromoCode() == null || payment.getPromoCode().isBlank()) {
            return;
        }
        promoCodeRepository.findByCodeIgnoreCase(payment.getPromoCode()).ifPresent(promo -> {
            if (!promoRedemptionRepository.existsByPromoCodeIdAndWorkspaceId(promo.getId(), workspaceId)) {
                PromoRedemptionEntity redemption = new PromoRedemptionEntity();
                redemption.setId(UuidV7.generate());
                redemption.setPromoCodeId(promo.getId());
                redemption.setWorkspaceId(workspaceId);
                redemption.setCreatedAt(Instant.now());
                promoRedemptionRepository.save(redemption);
                promo.setRedemptionCount(promo.getRedemptionCount() + 1);
                promoCodeRepository.save(promo);
            }
        });
    }

    private void ensureInvoice(PaymentEntity payment, String description) {
        boolean exists = invoiceRepository.findByWorkspaceIdOrderByCreatedAtDesc(payment.getWorkspaceId()).stream()
                .anyMatch(inv -> payment.getId().equals(inv.getPaymentId()));
        if (exists) {
            return;
        }
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(UuidV7.generate());
        invoice.setWorkspaceId(payment.getWorkspaceId());
        invoice.setPaymentId(payment.getId());
        invoice.setAmount(payment.getAmount());
        invoice.setAmountKopecks(payment.getAmountKopecks());
        invoice.setCurrency(payment.getCurrency());
        invoice.setStatus("paid");
        invoice.setPaidAt(Instant.now());
        invoice.setDescription(description);
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);
    }

    static Instant anniversaryEnd(Instant start, String billingPeriod) {
        ZonedDateTime zdt = start.atZone(ZoneOffset.UTC);
        if ("year".equals(billingPeriod)) {
            return zdt.plusYears(1).toInstant();
        }
        return zdt.plusMonths(1).toInstant();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
