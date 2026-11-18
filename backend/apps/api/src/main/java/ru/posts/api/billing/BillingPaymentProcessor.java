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
import ru.posts.persistence.entity.InvoiceEntity;
import ru.posts.persistence.entity.LedgerEntryEntity;
import ru.posts.persistence.entity.PaymentEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PromoRedemptionEntity;
import ru.posts.persistence.entity.SubscriptionEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.InvoiceRepository;
import ru.posts.persistence.repository.LedgerEntryRepository;
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
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final BillingFreezeService freezeService;

    public BillingPaymentProcessor(
            PaymentRepository paymentRepository,
            SubscriptionRepository subscriptionRepository,
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            InvoiceRepository invoiceRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PromoCodeRepository promoCodeRepository,
            PromoRedemptionRepository promoRedemptionRepository,
            BillingFreezeService freezeService) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.promoRedemptionRepository = promoRedemptionRepository;
        this.freezeService = freezeService;
    }

    @Transactional
    public void onPaymentSucceeded(PaymentResponse verified) {
        PaymentEntity payment = paymentRepository
                .findByProviderPaymentId(verified.id())
                .orElseGet(() -> createPaymentFromProvider(verified));
        if ("succeeded".equals(payment.getStatus())) {
            return;
        }

        // Serialize concurrent activations for the same workspace (duplicate webhook races).
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
        if (verified.paymentMethod() != null && verified.paymentMethod().id() != null) {
            Map<String, Object> metadata =
                    new HashMap<>(payment.getMetadata() == null ? Map.of() : payment.getMetadata());
            metadata.put("payment_method_id", verified.paymentMethod().id());
            payment.setMetadata(metadata);
        }
        paymentRepository.save(payment);

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
        if (verified.paymentMethod() != null && verified.paymentMethod().id() != null) {
            subscription.setProviderPaymentMethodId(verified.paymentMethod().id());
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
        ensureInvoice(payment);
        ensureLedgerCharge(payment);
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

        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setId(UuidV7.generate());
        entry.setWorkspaceId(payment.getWorkspaceId());
        entry.setAmount(
                refundAmount != null
                        ? refundAmount.negate()
                        : payment.getAmount().negate());
        entry.setKind("refund");
        entry.setRefId(payment.getId());
        entry.setDescription("YooKassa refund");
        entry.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(entry);
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
        payment.setCurrency(
                verified.amount() != null && verified.amount().currency() != null
                        ? verified.amount().currency()
                        : "RUB");
        payment.setStatus("pending");
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

    private void ensureInvoice(PaymentEntity payment) {
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
        invoice.setCurrency(payment.getCurrency());
        invoice.setStatus("paid");
        invoice.setDescription("Subscription payment");
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);
    }

    private void ensureLedgerCharge(PaymentEntity payment) {
        boolean exists = ledgerEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(payment.getWorkspaceId()).stream()
                .anyMatch(e -> payment.getId().equals(e.getRefId()) && "payment".equals(e.getKind()));
        if (exists) {
            return;
        }
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setId(UuidV7.generate());
        entry.setWorkspaceId(payment.getWorkspaceId());
        entry.setAmount(payment.getAmount());
        entry.setKind("payment");
        entry.setRefId(payment.getId());
        entry.setDescription("YooKassa payment");
        entry.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(entry);
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
