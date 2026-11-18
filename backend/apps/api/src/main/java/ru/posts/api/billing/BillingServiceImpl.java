package ru.posts.api.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AutopaySettingsEntity;
import ru.posts.persistence.entity.InvoiceEntity;
import ru.posts.persistence.entity.LedgerEntryEntity;
import ru.posts.persistence.entity.PaymentEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PromoCodeEntity;
import ru.posts.persistence.entity.PromoRedemptionEntity;
import ru.posts.persistence.entity.SubscriptionEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;
import ru.posts.persistence.repository.AutopaySettingsRepository;
import ru.posts.persistence.repository.InvoiceRepository;
import ru.posts.persistence.repository.LedgerEntryRepository;
import ru.posts.persistence.repository.PaymentRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.PromoRedemptionRepository;
import ru.posts.persistence.repository.SubscriptionRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.yookassa.Amount;
import ru.posts.yookassa.CreatePaymentRequest;
import ru.posts.yookassa.PaymentResponse;
import ru.posts.yookassa.Receipt;
import ru.posts.yookassa.YooKassaClient;
import ru.posts.yookassa.YooKassaException;
import ru.posts.yookassa.YooKassaProperties;

@Service
@Profile("!openapi")
public class BillingServiceImpl implements BillingService {

    private static final String IDEMPOTENCY_SCOPE_CHECKOUT = "POST /api/v1/billing/checkout";
    private static final String IDEMPOTENCY_SCOPE_TRIAL = "POST /api/v1/billing/trial/start";
    private static final String IDEMPOTENCY_SCOPE_INVOICE = "POST /api/v1/billing/invoices/request";
    private static final String PROVIDER = "yookassa";

    private final WorkspaceAccessService access;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AutopaySettingsRepository autopaySettingsRepository;
    private final IdempotencyService idempotencyService;
    private final BillingPromoValidator promoValidator;
    private final BillingProperties billingProperties;
    private final WalletLedgerService walletLedgerService;
    private final RussianRequisitesValidator requisitesValidator;
    private final InvoicePdfRenderer invoicePdfRenderer;
    private final YooKassaClient yooKassaClient;
    private final YooKassaProperties yooKassaProperties;
    private final TransactionTemplate writeTx;

    public BillingServiceImpl(
            WorkspaceAccessService access,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            PromoCodeRepository promoCodeRepository,
            PromoRedemptionRepository promoRedemptionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AutopaySettingsRepository autopaySettingsRepository,
            IdempotencyService idempotencyService,
            BillingPromoValidator promoValidator,
            BillingProperties billingProperties,
            WalletLedgerService walletLedgerService,
            RussianRequisitesValidator requisitesValidator,
            InvoicePdfRenderer invoicePdfRenderer,
            YooKassaClient yooKassaClient,
            YooKassaProperties yooKassaProperties,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.promoRedemptionRepository = promoRedemptionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.autopaySettingsRepository = autopaySettingsRepository;
        this.idempotencyService = idempotencyService;
        this.promoValidator = promoValidator;
        this.billingProperties = billingProperties;
        this.walletLedgerService = walletLedgerService;
        this.requisitesValidator = requisitesValidator;
        this.invoicePdfRenderer = invoicePdfRenderer;
        this.yooKassaClient = yooKassaClient;
        this.yooKassaProperties = yooKassaProperties;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingDtos.Plan> listPlans() {
        if (billingProperties.isBalanceModel()) {
            return planRepository.findAllByActiveTrueAndBillingModelOrderByPriceDayKopecksAsc("balance").stream()
                    .map(this::toPlanDto)
                    .toList();
        }
        return planRepository.findAllByOrderByPriceMonthAsc().stream()
                .map(this::toPlanDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BillingDtos.Subscription getSubscription(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "billing.view");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        return subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .map(sub -> toSubscriptionDto(sub, workspace))
                .orElseGet(() -> synthesizeSubscription(workspace));
    }

    @Override
    @Transactional
    public BillingDtos.Wallet getWallet(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "billing.view");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        WalletLedgerJdbc.WalletSnapshot wallet = walletLedgerService.getOrCreate(workspaceId);
        PlanEntity plan = workspace.getPlanId() == null
                ? null
                : planRepository.findById(workspace.getPlanId()).orElse(null);
        long priceDay = plan == null ? 0L : plan.getPriceDayKopecks();
        Integer days = priceDay > 0 ? (int) (wallet.balanceKopecks() / priceDay) : null;
        ApiEnums.BillingStatus status;
        try {
            status = ApiEnums.BillingStatus.valueOf(workspace.getBillingStatus());
        } catch (RuntimeException ex) {
            status = ApiEnums.BillingStatus.active;
        }
        return new BillingDtos.Wallet(
                workspaceId,
                wallet.balanceKopecks(),
                wallet.bonusBalanceKopecks(),
                wallet.refundableKopecks(),
                days,
                priceDay,
                plan == null ? "free" : plan.getCode(),
                status);
    }

    @Override
    public BillingDtos.CheckoutResponse checkout(
            UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_CHECKOUT,
                request,
                BillingDtos.CheckoutResponse.class,
                () -> writeTx.execute(status -> checkoutOnce(userId, request, idempotencyKey)));
    }

    private BillingDtos.CheckoutResponse checkoutOnce(
            UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey) {
        access.requirePermission(request.workspaceId(), userId, "billing.manage");
        WorkspaceEntity workspace = access.requireWorkspace(request.workspaceId());
        if (request.amountKopecks() != null && request.amountKopecks() > 0) {
            return checkoutTopup(workspace, request, idempotencyKey);
        }
        if (request.planCode() != null && !request.planCode().isBlank()) {
            return checkoutSubscription(workspace, request, idempotencyKey);
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ProblemCodes.VALIDATION_FAILED,
                "Provide amountKopecks for top-up or planCode for subscription checkout");
    }

    private BillingDtos.CheckoutResponse checkoutTopup(
            WorkspaceEntity workspace, BillingDtos.CheckoutRequest request, String idempotencyKey) {
        long amountKopecks = request.amountKopecks() == null ? 0L : request.amountKopecks();
        if (amountKopecks < billingProperties.getAutopayMinTopupKopecks()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Top-up amount below minimum");
        }
        BigDecimal amount = MoneyKopecks.toRubles(amountKopecks);
        String promoCode = blankToNull(request.promoCode());
        if (promoCode != null) {
            PromoCodeEntity promo = promoValidator.requireValid(promoCode, workspace.getId());
            if ("percent".equals(promo.getKind())) {
                amount = promoValidator.applyDiscount(amount, promo);
                amountKopecks = MoneyKopecks.toKopecks(amount);
            } else if (!"fixed".equals(promo.getKind())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ProblemCodes.PROMO_INVALID,
                        "Only percent promo applies at top-up checkout; use /promo/apply for balance credit");
            }
        }
        if (amountKopecks <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Top-up amount must be positive");
        }

        UserEntity owner = requireOwner(workspace);
        UUID paymentId = UuidV7.generate();
        String yooIdempotenceKey =
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : paymentId.toString();

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("workspaceId", workspace.getId().toString());
        metadata.put("paymentId", paymentId.toString());
        metadata.put("purpose", "topup");
        if (promoCode != null) {
            metadata.put("promoCode", promoCode);
        }

        Amount yooAmount = new Amount(amount.toPlainString(), "RUB");
        String description = "Пополнение баланса Posts";
        Receipt receipt = new Receipt(
                new Receipt.Customer(owner.getEmail()),
                List.of(new Receipt.ReceiptItem(description, "1.00", yooAmount, yooKassaProperties.vatCode())));

        PaymentResponse yooPayment;
        try {
            yooPayment = yooKassaClient.createPayment(
                    new CreatePaymentRequest(yooAmount, description, metadata, receipt), yooIdempotenceKey);
        } catch (YooKassaException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ProblemCodes.PAYMENT_PROVIDER_ERROR,
                    "YooKassa payment failed: " + ex.getMessage());
        }
        return persistPendingPayment(
                paymentId,
                workspace.getId(),
                yooPayment,
                amount,
                amountKopecks,
                "topup",
                null,
                null,
                promoCode,
                metadata,
                yooIdempotenceKey);
    }

    private BillingDtos.CheckoutResponse checkoutSubscription(
            WorkspaceEntity workspace, BillingDtos.CheckoutRequest request, String idempotencyKey) {
        PlanEntity plan = planRepository
                .findByCode(request.planCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Plan not found"));
        if ("free".equals(plan.getCode()) || "enterprise".equals(plan.getCode())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Plan is not available for checkout");
        }
        if (request.period() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "period is required");
        }
        String period = request.period().name();
        BigDecimal amount = "year".equals(period) ? plan.getPriceYear() : plan.getPriceMonth();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Plan price is not configured");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        String promoCode = blankToNull(request.promoCode());
        if (promoCode != null) {
            PromoCodeEntity promo = promoValidator.requireValid(promoCode, workspace.getId());
            if ("trial_days".equals(promo.getKind())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ProblemCodes.PROMO_INVALID,
                        "Trial promo codes must be applied via /promo/apply");
            }
            amount = promoValidator.applyDiscount(amount, promo);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.PROMO_INVALID, "Discounted amount must be positive");
            }
        }
        UserEntity owner = requireOwner(workspace);
        UUID paymentId = UuidV7.generate();
        String yooIdempotenceKey =
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : paymentId.toString();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("workspaceId", workspace.getId().toString());
        metadata.put("paymentId", paymentId.toString());
        metadata.put("planCode", plan.getCode());
        metadata.put("planId", plan.getId().toString());
        metadata.put("period", period);
        metadata.put("purpose", "subscription");
        if (promoCode != null) {
            metadata.put("promoCode", promoCode);
        }
        Amount yooAmount = new Amount(amount.toPlainString(), "RUB");
        String description = plan.getName() + " / " + ("year".equals(period) ? "год" : "месяц");
        Receipt receipt = new Receipt(
                new Receipt.Customer(owner.getEmail()),
                List.of(new Receipt.ReceiptItem(description, "1.00", yooAmount, yooKassaProperties.vatCode())));
        PaymentResponse yooPayment;
        try {
            yooPayment = yooKassaClient.createPayment(
                    new CreatePaymentRequest(yooAmount, description, metadata, receipt), yooIdempotenceKey);
        } catch (YooKassaException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ProblemCodes.PAYMENT_PROVIDER_ERROR,
                    "YooKassa payment failed: " + ex.getMessage());
        }
        return persistPendingPayment(
                paymentId,
                workspace.getId(),
                yooPayment,
                amount,
                MoneyKopecks.toKopecks(amount),
                "subscription",
                plan.getId(),
                period,
                promoCode,
                metadata,
                yooIdempotenceKey);
    }

    private BillingDtos.CheckoutResponse persistPendingPayment(
            UUID paymentId,
            UUID workspaceId,
            PaymentResponse yooPayment,
            BigDecimal amount,
            long amountKopecks,
            String purpose,
            UUID planId,
            String period,
            String promoCode,
            Map<String, String> metadata,
            String yooIdempotenceKey) {
        PaymentEntity existing =
                paymentRepository.findByProviderPaymentId(yooPayment.id()).orElse(null);
        if (existing != null) {
            return new BillingDtos.CheckoutResponse(existing.getId(), existing.getConfirmationUrl(), PROVIDER);
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setId(paymentId);
        payment.setWorkspaceId(workspaceId);
        payment.setProvider(PROVIDER);
        payment.setProviderPaymentId(yooPayment.id());
        payment.setAmount(amount);
        payment.setAmountKopecks(amountKopecks);
        payment.setCurrency("RUB");
        payment.setStatus("pending");
        payment.setPurpose(purpose);
        payment.setPlanId(planId);
        payment.setBillingPeriod(period);
        payment.setPromoCode(promoCode);
        payment.setConfirmationUrl(
                yooPayment.confirmation() == null
                        ? null
                        : yooPayment.confirmation().confirmationUrl());
        payment.setMetadata(new HashMap<>(metadata));
        payment.setIdempotenceKey(yooIdempotenceKey);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            PaymentEntity raced =
                    paymentRepository.findByProviderPaymentId(yooPayment.id()).orElseThrow(() -> ex);
            return new BillingDtos.CheckoutResponse(raced.getId(), raced.getConfirmationUrl(), PROVIDER);
        }
        return new BillingDtos.CheckoutResponse(payment.getId(), payment.getConfirmationUrl(), PROVIDER);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.InvoicePage listInvoices(UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "billing.view");
        List<InvoiceEntity> all = invoiceRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        CursorPages.Slice<InvoiceEntity> slice = CursorPages.slice(
                all,
                page == null ? null : page.cursor(),
                page == null ? 50 : page.limitOrDefault(),
                InvoiceEntity::getId);
        List<BillingDtos.Invoice> items =
                slice.items().stream().map(this::toInvoiceDto).toList();
        return new CursorPage.InvoicePage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    public BillingDtos.Invoice requestInvoice(
            UUID userId, BillingDtos.RequestInvoiceRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_INVOICE,
                request,
                BillingDtos.Invoice.class,
                () -> writeTx.execute(status -> requestInvoiceOnce(userId, request)));
    }

    private BillingDtos.Invoice requestInvoiceOnce(UUID userId, BillingDtos.RequestInvoiceRequest request) {
        access.requirePermission(request.workspaceId(), userId, "billing.manage");
        access.requireWorkspace(request.workspaceId());
        requisitesValidator.validate(request.buyer());
        long amountKopecks = request.amountKopecks();
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(UuidV7.generate());
        invoice.setWorkspaceId(request.workspaceId());
        invoice.setAmount(MoneyKopecks.toRubles(amountKopecks));
        invoice.setAmountKopecks(amountKopecks);
        invoice.setCurrency("RUB");
        invoice.setStatus("issued");
        invoice.setInvoiceNumber(
                "INV-" + invoice.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", request.buyer().name());
        req.put("inn", request.buyer().inn());
        if (request.buyer().kpp() != null) {
            req.put("kpp", request.buyer().kpp());
        }
        if (request.buyer().ogrn() != null) {
            req.put("ogrn", request.buyer().ogrn());
        }
        if (request.buyer().address() != null) {
            req.put("address", request.buyer().address());
        }
        invoice.setBuyerRequisites(req);
        invoice.setDescription("Счёт на пополнение баланса");
        invoice.setPdfUrl("/api/v1/billing/invoices/" + invoice.getId() + "/pdf");
        invoice.setActPdfUrl("/api/v1/billing/invoices/" + invoice.getId() + "/act");
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        return toInvoiceDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] invoicePdf(UUID userId, UUID invoiceId) {
        InvoiceEntity invoice = requireInvoiceAccess(userId, invoiceId);
        return invoicePdfRenderer.renderInvoice(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] invoiceActPdf(UUID userId, UUID invoiceId) {
        InvoiceEntity invoice = requireInvoiceAccess(userId, invoiceId);
        return invoicePdfRenderer.renderAct(invoice);
    }

    @Override
    @Transactional
    public BillingDtos.ApplyPromoResponse applyPromo(UUID userId, BillingDtos.ApplyPromoRequest request) {
        access.requirePermission(request.workspaceId(), userId, "billing.apply_promo");
        WorkspaceEntity workspace = access.requireWorkspace(request.workspaceId());
        PromoCodeEntity promo = promoValidator.requireValid(request.code(), workspace.getId());

        Long credited = null;
        if ("trial_days".equals(promo.getKind())) {
            applyTrialDaysPromo(userId, workspace, promo);
        } else if ("fixed".equals(promo.getKind()) && isBalanceWorkspace(workspace)) {
            long kopecks = MoneyKopecks.toKopecks(promo.getValue());
            String key = "promo:" + promo.getId() + ":" + workspace.getId();
            walletLedgerService.credit(
                    workspace.getId(), kopecks, "promo", true, key, promo.getId(), "Promo " + promo.getCode());
            redeemPromo(promo, workspace.getId(), userId);
            credited = kopecks;
        } else if ("percent".equals(promo.getKind()) || "fixed".equals(promo.getKind())) {
            // Discount promos are applied at checkout; acknowledge validity.
            redeemPromo(promo, workspace.getId(), userId);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.PROMO_INVALID, "Unsupported promo kind");
        }

        return new BillingDtos.ApplyPromoResponse(
                promo.getCode(), promo.getKind(), promo.getValue(), promo.getValidUntil(), credited);
    }

    @Override
    public BillingDtos.Subscription startTrial(
            UUID userId, BillingDtos.StartTrialRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_TRIAL,
                request,
                BillingDtos.Subscription.class,
                () -> writeTx.execute(status -> startTrialOnce(userId, request)));
    }

    private BillingDtos.Subscription startTrialOnce(UUID userId, BillingDtos.StartTrialRequest request) {
        access.requirePermission(request.workspaceId(), userId, "billing.manage");
        WorkspaceEntity workspace = access.requireWorkspace(request.workspaceId());

        if ("trial".equals(workspace.getBillingStatus())
                && workspace.getTrialEndsAt() != null
                && workspace.getTrialEndsAt().isAfter(Instant.now())) {
            return getSubscription(userId, workspace.getId());
        }

        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (user.isTrialUsed()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.TRIAL_ALREADY_USED, "Pro trial already used for this account");
        }

        PlanEntity trialPlan = planRepository
                .findByCode(isBalanceWorkspace(workspace) ? "daily_25" : "pro")
                .or(() -> planRepository.findByCode("pro"))
                .orElseThrow(() -> new IllegalStateException("trial plan missing"));
        Instant now = Instant.now();
        Instant trialEnds = now.plus(billingProperties.getTrialDays(), ChronoUnit.DAYS);

        user.setTrialUsed(true);
        user.setUpdatedAt(now);
        userRepository.save(user);

        workspace.setPlanId(trialPlan.getId());
        workspace.setBillingStatus("trial");
        workspace.setTrialEndsAt(trialEnds);
        workspace.setUpdatedAt(now);
        workspaceRepository.save(workspace);

        walletLedgerService.getOrCreate(workspace.getId());
        if (billingProperties.getRegistrationBonusKopecks() > 0) {
            walletLedgerService.credit(
                    workspace.getId(),
                    billingProperties.getRegistrationBonusKopecks(),
                    "promo",
                    true,
                    "registration-bonus:" + workspace.getId(),
                    null,
                    "Registration bonus");
        }

        SubscriptionEntity subscription = subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspace.getId())
                .orElseGet(SubscriptionEntity::new);
        if (subscription.getId() == null) {
            subscription.setId(UuidV7.generate());
            subscription.setWorkspaceId(workspace.getId());
            subscription.setCreatedAt(now);
        }
        subscription.setPlanId(trialPlan.getId());
        subscription.setStatus("trialing");
        subscription.setPeriodStart(now);
        subscription.setPeriodEnd(trialEnds);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setBillingPeriod("month");
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        return toSubscriptionDto(subscription, workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.TransactionPage listTransactions(
            UUID userId, UUID workspaceId, String kind, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "billing.view");
        List<LedgerEntryEntity> all = ledgerEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        if (kind != null && !kind.isBlank()) {
            all = all.stream().filter(e -> kind.equals(e.getKind())).toList();
        }
        CursorPages.Slice<LedgerEntryEntity> slice = CursorPages.slice(
                all,
                page == null ? null : page.cursor(),
                page == null ? 50 : page.limitOrDefault(),
                LedgerEntryEntity::getId);
        List<BillingDtos.Transaction> items =
                slice.items().stream().map(this::toTransactionDto).toList();
        return new CursorPage.TransactionPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.PaymentPage listPayments(UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "billing.view");
        List<PaymentEntity> all = paymentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        CursorPages.Slice<PaymentEntity> slice = CursorPages.slice(
                all,
                page == null ? null : page.cursor(),
                page == null ? 50 : page.limitOrDefault(),
                PaymentEntity::getId);
        List<BillingDtos.PaymentListItem> items =
                slice.items().stream().map(this::toPaymentListItem).toList();
        return new CursorPage.PaymentPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public BillingDtos.AutopaySettings getAutopay(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "billing.view");
        access.requireWorkspace(workspaceId);
        return toAutopayDto(
                autopaySettingsRepository.findById(workspaceId).orElseGet(() -> defaultAutopay(workspaceId)));
    }

    @Override
    @Transactional
    public BillingDtos.AutopaySettings updateAutopay(UUID userId, BillingDtos.UpdateAutopayRequest request) {
        access.requirePermission(request.workspaceId(), userId, "billing.manage");
        access.requireWorkspace(request.workspaceId());
        AutopaySettingsEntity settings = autopaySettingsRepository
                .findById(request.workspaceId())
                .orElseGet(() -> defaultAutopay(request.workspaceId()));
        settings.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (request.thresholdKopecks() != null) {
            settings.setThresholdKopecks(request.thresholdKopecks());
        }
        if (request.topupAmountKopecks() != null) {
            if (request.topupAmountKopecks() < billingProperties.getAutopayMinTopupKopecks()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Autopay top-up below minimum");
            }
            settings.setTopupAmountKopecks(request.topupAmountKopecks());
        }
        if (settings.isEnabled()
                && (settings.getProviderPaymentMethodId() == null
                        || settings.getProviderPaymentMethodId().isBlank())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "Saved payment method required to enable autopay");
        }
        settings.setUpdatedAt(Instant.now());
        return toAutopayDto(autopaySettingsRepository.save(settings));
    }

    @Override
    @Transactional(readOnly = true)
    public BillingDtos.PaymentMethod getPaymentMethod(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "billing.view");
        AutopaySettingsEntity settings =
                autopaySettingsRepository.findById(workspaceId).orElse(null);
        if (settings != null && settings.getProviderPaymentMethodId() != null) {
            return new BillingDtos.PaymentMethod(
                    settings.getProviderPaymentMethodId(), settings.getCardMask(), "bank_card");
        }
        return subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .filter(s -> s.getProviderPaymentMethodId() != null)
                .map(s -> new BillingDtos.PaymentMethod(s.getProviderPaymentMethodId(), null, "bank_card"))
                .orElse(null);
    }

    @Override
    @Transactional
    public void deletePaymentMethod(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "billing.manage");
        autopaySettingsRepository.findById(workspaceId).ifPresent(settings -> {
            settings.setProviderPaymentMethodId(null);
            settings.setCardMask(null);
            settings.setEnabled(false);
            settings.setUpdatedAt(Instant.now());
            autopaySettingsRepository.save(settings);
        });
        subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .ifPresent(sub -> {
                    sub.setProviderPaymentMethodId(null);
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);
                });
    }

    @Override
    @Transactional
    public BillingDtos.Subscription changePlan(UUID userId, BillingDtos.ChangePlanRequest request) {
        access.requirePermission(request.workspaceId(), userId, "billing.manage");
        WorkspaceEntity workspace = access.requireWorkspace(request.workspaceId());
        PlanEntity plan = planRepository
                .findByCode(request.planCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Plan not found"));
        if (!plan.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Plan is inactive");
        }
        if (isBalanceWorkspace(workspace)
                && !"balance".equals(plan.getBillingModel())
                && !"free".equals(plan.getCode())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Plan is not available in balance mode");
        }
        workspace.setPlanId(plan.getId());
        workspace.setUpdatedAt(Instant.now());
        workspaceRepository.save(workspace);

        SubscriptionEntity subscription = subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspace.getId())
                .orElseGet(SubscriptionEntity::new);
        Instant now = Instant.now();
        if (subscription.getId() == null) {
            subscription.setId(UuidV7.generate());
            subscription.setWorkspaceId(workspace.getId());
            subscription.setCreatedAt(now);
            subscription.setPeriodStart(now);
            subscription.setPeriodEnd(now.plus(30, ChronoUnit.DAYS));
            subscription.setStatus("active");
        }
        subscription.setPlanId(plan.getId());
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);
        return toSubscriptionDto(subscription, workspace);
    }

    @Override
    @Transactional
    public BillingDtos.AutopaySettings setAutoRenew(UUID userId, BillingDtos.SetAutoRenewRequest request) {
        return updateAutopay(
                userId, new BillingDtos.UpdateAutopayRequest(request.workspaceId(), request.enabled(), null, null));
    }

    private void applyTrialDaysPromo(UUID userId, WorkspaceEntity workspace, PromoCodeEntity promo) {
        PlanEntity pro =
                planRepository.findByCode("pro").orElseThrow(() -> new IllegalStateException("pro plan missing"));
        Instant now = Instant.now();
        int days = promo.getValue().intValue();
        Instant base =
                workspace.getTrialEndsAt() != null && workspace.getTrialEndsAt().isAfter(now)
                        ? workspace.getTrialEndsAt()
                        : now;
        Instant trialEnds = base.plus(days, ChronoUnit.DAYS);

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user != null && !user.isTrialUsed()) {
            user.setTrialUsed(true);
            user.setUpdatedAt(now);
            userRepository.save(user);
        }

        workspace.setPlanId(pro.getId());
        workspace.setBillingStatus("trial");
        workspace.setTrialEndsAt(trialEnds);
        workspace.setUpdatedAt(now);
        workspaceRepository.save(workspace);

        SubscriptionEntity subscription = subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspace.getId())
                .orElseGet(SubscriptionEntity::new);
        if (subscription.getId() == null) {
            subscription.setId(UuidV7.generate());
            subscription.setWorkspaceId(workspace.getId());
            subscription.setCreatedAt(now);
        }
        subscription.setPlanId(pro.getId());
        subscription.setStatus("trialing");
        subscription.setPeriodStart(now);
        subscription.setPeriodEnd(trialEnds);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        redeemPromo(promo, workspace.getId(), userId);
    }

    private void redeemPromo(PromoCodeEntity promo, UUID workspaceId, UUID userId) {
        if (promoRedemptionRepository.existsByPromoCodeIdAndWorkspaceId(promo.getId(), workspaceId)) {
            return;
        }
        PromoRedemptionEntity redemption = new PromoRedemptionEntity();
        redemption.setId(UuidV7.generate());
        redemption.setPromoCodeId(promo.getId());
        redemption.setWorkspaceId(workspaceId);
        redemption.setUserId(userId);
        redemption.setCreatedAt(Instant.now());
        promoRedemptionRepository.save(redemption);
        promo.setRedemptionCount(promo.getRedemptionCount() + 1);
        promoCodeRepository.save(promo);
    }

    private AutopaySettingsEntity defaultAutopay(UUID workspaceId) {
        AutopaySettingsEntity settings = new AutopaySettingsEntity();
        settings.setWorkspaceId(workspaceId);
        settings.setEnabled(false);
        settings.setThresholdKopecks(billingProperties.getAutopayDefaultThresholdKopecks());
        settings.setTopupAmountKopecks(billingProperties.getAutopayDefaultTopupKopecks());
        settings.setUpdatedAt(Instant.now());
        return settings;
    }

    private InvoiceEntity requireInvoiceAccess(UUID userId, UUID invoiceId) {
        InvoiceEntity invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Invoice not found"));
        access.requirePermission(invoice.getWorkspaceId(), userId, "billing.view");
        return invoice;
    }

    private UserEntity requireOwner(WorkspaceEntity workspace) {
        return userRepository
                .findById(workspace.getOwnerUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Owner not found"));
    }

    private boolean isBalanceWorkspace(WorkspaceEntity workspace) {
        if (workspace.getBillingModel() != null) {
            return "balance".equalsIgnoreCase(workspace.getBillingModel());
        }
        return billingProperties.isBalanceModel();
    }

    private BillingDtos.Subscription synthesizeSubscription(WorkspaceEntity workspace) {
        String planCode = "free";
        if (workspace.getPlanId() != null) {
            planCode = planRepository
                    .findById(workspace.getPlanId())
                    .map(PlanEntity::getCode)
                    .orElse("free");
        }
        ApiEnums.BillingStatus status;
        try {
            status = ApiEnums.BillingStatus.valueOf(workspace.getBillingStatus());
        } catch (RuntimeException ex) {
            status = ApiEnums.BillingStatus.active;
        }
        return new BillingDtos.Subscription(
                null,
                workspace.getId(),
                planCode,
                status,
                workspace.getCreatedAt(),
                workspace.getCurrentPeriodEnd() != null ? workspace.getCurrentPeriodEnd() : workspace.getTrialEndsAt(),
                false,
                workspace.getTrialEndsAt(),
                workspace.getBillingModel());
    }

    private BillingDtos.Subscription toSubscriptionDto(SubscriptionEntity sub, WorkspaceEntity workspace) {
        String planCode = planRepository
                .findById(sub.getPlanId())
                .map(PlanEntity::getCode)
                .orElse("free");
        ApiEnums.BillingStatus status = mapSubscriptionStatus(sub.getStatus(), workspace.getBillingStatus());
        return new BillingDtos.Subscription(
                sub.getId(),
                sub.getWorkspaceId(),
                planCode,
                status,
                sub.getPeriodStart(),
                sub.getPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                workspace.getTrialEndsAt(),
                workspace.getBillingModel());
    }

    private static ApiEnums.BillingStatus mapSubscriptionStatus(String subscriptionStatus, String workspaceStatus) {
        if (workspaceStatus != null) {
            try {
                return ApiEnums.BillingStatus.valueOf(workspaceStatus);
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return switch (subscriptionStatus == null ? "" : subscriptionStatus) {
            case "trialing" -> ApiEnums.BillingStatus.trial;
            case "past_due" -> ApiEnums.BillingStatus.grace;
            default -> ApiEnums.BillingStatus.active;
        };
    }

    private BillingDtos.Plan toPlanDto(PlanEntity plan) {
        BigDecimal monthDisplay = plan.getPriceDayKopecks() > 0
                ? MoneyKopecks.toRubles(plan.getPriceDayKopecks() * 30L)
                : plan.getPriceMonth();
        return new BillingDtos.Plan(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPriceMonth(),
                plan.getPriceYear(),
                plan.getPriceDayKopecks(),
                monthDisplay,
                plan.getBillingModel(),
                plan.getLimits() == null ? Map.of() : plan.getLimits());
    }

    private BillingDtos.Invoice toInvoiceDto(InvoiceEntity invoice) {
        return new BillingDtos.Invoice(
                invoice.getId(),
                invoice.getWorkspaceId(),
                invoice.getAmount(),
                invoice.getAmountKopecks(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getInvoiceNumber(),
                invoice.getPdfUrl(),
                invoice.getActPdfUrl(),
                invoice.getBuyerRequisites() == null ? Map.of() : invoice.getBuyerRequisites(),
                invoice.getPaidAt(),
                invoice.getCreatedAt());
    }

    private BillingDtos.Transaction toTransactionDto(LedgerEntryEntity entry) {
        String method = null;
        String receiptUrl = null;
        UUID paymentId = null;
        if (entry.getRefId() != null) {
            PaymentEntity payment = paymentRepository.findById(entry.getRefId()).orElse(null);
            if (payment != null) {
                paymentId = payment.getId();
                method = payment.getProvider();
                receiptUrl = payment.getReceiptId();
            }
        }
        return new BillingDtos.Transaction(
                entry.getId(),
                entry.getCreatedAt(),
                entry.getAmountKopecks(),
                entry.getKind(),
                entry.isBonus(),
                null,
                method,
                receiptUrl,
                null,
                entry.getDescription(),
                paymentId,
                null);
    }

    private BillingDtos.PaymentListItem toPaymentListItem(PaymentEntity payment) {
        String method = null;
        if (payment.getMetadata() != null && payment.getMetadata().get("payment_method_id") != null) {
            method = String.valueOf(payment.getMetadata().get("payment_method_id"));
        }
        return new BillingDtos.PaymentListItem(
                payment.getId(),
                payment.getCreatedAt(),
                payment.getAmountKopecks(),
                payment.getStatus(),
                payment.getPurpose(),
                payment.getProvider(),
                payment.getConfirmationUrl(),
                payment.getReceiptId(),
                method);
    }

    private BillingDtos.AutopaySettings toAutopayDto(AutopaySettingsEntity settings) {
        BillingDtos.PaymentMethod method = null;
        if (settings.getProviderPaymentMethodId() != null) {
            method = new BillingDtos.PaymentMethod(
                    settings.getProviderPaymentMethodId(), settings.getCardMask(), "bank_card");
        }
        return new BillingDtos.AutopaySettings(
                settings.getWorkspaceId(),
                settings.isEnabled(),
                settings.getThresholdKopecks(),
                settings.getTopupAmountKopecks(),
                method,
                settings.getLastTriggeredAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
