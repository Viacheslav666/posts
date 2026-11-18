package ru.posts.api.billing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.PaymentEntity;
import ru.posts.persistence.entity.PromoCodeEntity;
import ru.posts.persistence.entity.SubscriptionEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.PaymentRepository;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.SubscriptionRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BillingYooKassaIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String YOO_PAYMENT_ID = "test-yoo-payment-be12-0001";

    @RegisterExtension
    static WireMockExtension yookassa = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @DynamicPropertySource
    static void yookassaUrl(DynamicPropertyRegistry registry) {
        registry.add("posts.billing.yookassa.base-url", yookassa::baseUrl);
        registry.add("posts.billing.yookassa.connect-timeout", () -> "2s");
        registry.add("posts.billing.yookassa.read-timeout", () -> "5s");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PromoCodeRepository promoCodeRepository;

    @Autowired
    BillingPaymentProcessor paymentProcessor;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        yookassa.resetAll();
    }

    @Test
    @DisplayName("checkout creates YooKassa payment and stores pending row")
    void checkoutCreatesPayment() throws Exception {
        stubCreatePayment(YOO_PAYMENT_ID, "990.00");
        String token = registerVerifyLogin("bill-checkout-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);

        JsonNode body = readJson(mockMvc.perform(post("/api/v1/billing/checkout")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "checkout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "planCode", "pro",
                                "period", "month"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("yookassa"))
                .andExpect(jsonPath("$.confirmationUrl").value("https://yookassa.test/confirm/" + YOO_PAYMENT_ID))
                .andReturn());

        UUID paymentId = UUID.fromString(body.path("paymentId").asText());
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getProviderPaymentId()).isEqualTo(YOO_PAYMENT_ID);
        assertThat(payment.getStatus()).isEqualTo("pending");
        assertThat(payment.getWorkspaceId()).isEqualTo(workspaceId);

        yookassa.verify(postRequestedFor(urlPathEqualTo("/payments"))
                .withRequestBody(containing("\"save_payment_method\":true"))
                .withRequestBody(containing(workspaceId.toString())));
    }

    @Test
    @DisplayName("webhook payment.succeeded activates subscription")
    void webhookActivatesSubscription() throws Exception {
        String providerPaymentId = "test-yoo-payment-be12-success";
        stubCreatePayment(providerPaymentId, "990.00");
        stubGetPayment(providerPaymentId, "succeeded", true, "pm-saved-1");

        String token = registerVerifyLogin("bill-wh-ok-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        checkout(token, workspaceId, providerPaymentId);

        mockMvc.perform(post("/webhooks/yookassa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload("payment.succeeded", providerPaymentId)))
                .andExpect(status().isOk());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            WorkspaceEntity workspace =
                    workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(workspace.getBillingStatus()).isEqualTo("active");
            assertThat(workspace.getCurrentPeriodEnd()).isNotNull();
            PaymentEntity payment =
                    paymentRepository.findByProviderPaymentId(providerPaymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo("succeeded");
            SubscriptionEntity sub = subscriptionRepository
                    .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                    .orElseThrow();
            assertThat(sub.getStatus()).isEqualTo("active");
            assertThat(sub.getProviderPaymentMethodId()).isEqualTo("pm-saved-1");
        });
    }

    @Test
    @DisplayName("duplicate webhook is idempotent")
    void duplicateWebhookIdempotent() throws Exception {
        String providerPaymentId = "test-yoo-payment-be12-dup";
        stubCreatePayment(providerPaymentId, "990.00");
        stubGetPayment(providerPaymentId, "succeeded", true, "pm-dup");

        String token = registerVerifyLogin("bill-wh-dup-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        checkout(token, workspaceId, providerPaymentId);

        String payload = webhookPayload("payment.succeeded", providerPaymentId);
        mockMvc.perform(post("/webhooks/yookassa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(paymentRepository
                        .findByProviderPaymentId(providerPaymentId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo("succeeded"));

        mockMvc.perform(post("/webhooks/yookassa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(subscriptionRepository.findByWorkspaceId(workspaceId)).hasSize(1);
    }

    @Test
    @DisplayName("concurrent webhooks race → single activation")
    void concurrentWebhooksSingleActivation() throws Exception {
        String providerPaymentId = "test-yoo-payment-be12-race";
        stubCreatePayment(providerPaymentId, "990.00");
        stubGetPayment(providerPaymentId, "succeeded", true, "pm-race");

        String token = registerVerifyLogin("bill-wh-race-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        checkout(token, workspaceId, providerPaymentId);

        // Race the activation handler itself (MockMvc is not thread-safe).
        var verified = new ru.posts.yookassa.PaymentResponse(
                providerPaymentId,
                "succeeded",
                true,
                new ru.posts.yookassa.Amount("990.00", "RUB"),
                null,
                new ru.posts.yookassa.PaymentMethod("bank_card", "pm-race", true),
                Map.of("workspaceId", workspaceId.toString()),
                null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger oks = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            @SuppressWarnings("unused")
            var ignored = pool.submit(() -> {
                try {
                    start.await();
                    paymentProcessor.onPaymentSucceeded(verified);
                    oks.incrementAndGet();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(oks.get()).isEqualTo(2);

        assertThat(paymentRepository
                        .findByProviderPaymentId(providerPaymentId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo("succeeded");
        assertThat(subscriptionRepository.findByWorkspaceId(workspaceId)).hasSize(1);
        assertThat(workspaceRepository.findById(workspaceId).orElseThrow().getBillingStatus())
                .isEqualTo("active");
    }

    @Test
    @DisplayName("payment.canceled leaves plan unchanged")
    void paymentCanceledLeavesPlan() throws Exception {
        String providerPaymentId = "test-yoo-payment-be12-cancel";
        stubCreatePayment(providerPaymentId, "390.00");
        stubGetPayment(providerPaymentId, "canceled", false, null);

        String token = registerVerifyLogin("bill-wh-cancel-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        WorkspaceEntity before = workspaceRepository.findById(workspaceId).orElseThrow();
        UUID planBefore = before.getPlanId();
        String statusBefore = before.getBillingStatus();

        checkout(token, workspaceId, providerPaymentId, "start");

        mockMvc.perform(post("/webhooks/yookassa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload("payment.canceled", providerPaymentId)))
                .andExpect(status().isOk());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(paymentRepository
                        .findByProviderPaymentId(providerPaymentId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo("cancelled"));

        WorkspaceEntity after = workspaceRepository.findById(workspaceId).orElseThrow();
        assertThat(after.getPlanId()).isEqualTo(planBefore);
        assertThat(after.getBillingStatus()).isEqualTo(statusBefore);
    }

    @Test
    @DisplayName("applyPromo trial_days extends trial")
    void applyPromoTrialDays() throws Exception {
        String token = registerVerifyLogin("bill-promo-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        seedTrialPromo("TRIAL7DAYS", 7);

        mockMvc.perform(post("/api/v1/billing/promo/apply")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("workspaceId", workspaceId.toString(), "code", "TRIAL7DAYS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("trial_days"));

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        assertThat(workspace.getBillingStatus()).isEqualTo("trial");
        assertThat(workspace.getTrialEndsAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("startTrial once then 409 trial_already_used")
    void startTrialOnceThenConflict() throws Exception {
        String token = registerVerifyLogin("bill-trial-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);

        mockMvc.perform(post("/api/v1/billing/trial/start")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workspaceId", workspaceId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("trial"));

        UserEntity user = userRepository.findAll().stream()
                .filter(u -> u.getEmail().contains("bill-trial-"))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(user.isTrialUsed()).isTrue();

        mockMvc.perform(post("/api/v1/billing/trial/start")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workspaceId", workspaceId.toString()))))
                .andExpect(status().isOk()); // idempotent while still on trial

        // Force trial used + not on trial for 409 path: create second workspace and expire trial flag scenario
        JsonNode created = readJson(mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Second WS"))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID secondWs = UUID.fromString(created.path("id").asText());
        WorkspaceEntity first = workspaceRepository.findById(workspaceId).orElseThrow();
        first.setBillingStatus("active");
        first.setTrialEndsAt(null);
        workspaceRepository.save(first);

        mockMvc.perform(post("/api/v1/billing/trial/start")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("workspaceId", secondWs.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trial_already_used"));
    }

    private void checkout(String token, UUID workspaceId, String providerPaymentId) throws Exception {
        checkout(token, workspaceId, providerPaymentId, "pro");
    }

    private void checkout(String token, UUID workspaceId, String providerPaymentId, String planCode) throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .with(csrf())
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "idem-" + providerPaymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(), "planCode", planCode, "period", "month"))))
                .andExpect(status().isOk());
    }

    private void stubCreatePayment(String paymentId, String amount) {
        yookassa.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/payments"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "id": "%s",
                                  "status": "pending",
                                  "paid": false,
                                  "amount": {"value": "%s", "currency": "RUB"},
                                  "confirmation": {
                                    "type": "redirect",
                                    "confirmation_url": "https://yookassa.test/confirm/%s"
                                  }
                                }
                                """
                                        .formatted(paymentId, amount, paymentId))));
    }

    private void stubGetPayment(String paymentId, String status, boolean paid, String paymentMethodId) {
        String paymentMethodJson = paymentMethodId == null
                ? "null"
                : "{\"type\":\"bank_card\",\"id\":\"%s\",\"saved\":true}".formatted(paymentMethodId);
        yookassa.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/payments/" + paymentId))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "id": "%s",
                                  "status": "%s",
                                  "paid": %s,
                                  "amount": {"value": "990.00", "currency": "RUB"},
                                  "payment_method": %s,
                                  "metadata": {}
                                }
                                """
                                        .formatted(paymentId, status, paid, paymentMethodJson))));
    }

    private static String webhookPayload(String event, String paymentId) {
        return """
                {
                  "type": "notification",
                  "event": "%s",
                  "object": {
                    "id": "%s",
                    "status": "%s",
                    "paid": %s,
                    "amount": {"value": "990.00", "currency": "RUB"}
                  }
                }
                """
                .formatted(
                        event,
                        paymentId,
                        event.contains("cancel") ? "canceled" : "succeeded",
                        !event.contains("cancel"));
    }

    private void seedTrialPromo(String code, int days) {
        PromoCodeEntity promo = new PromoCodeEntity();
        promo.setId(UuidV7.generate());
        promo.setCode(code);
        promo.setKind("trial_days");
        promo.setValue(BigDecimal.valueOf(days));
        promo.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        promo.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        promo.setActive(true);
        promo.setOncePerWorkspace(true);
        promo.setRedemptionCount(0);
        promo.setCreatedAt(Instant.now());
        promoCodeRepository.save(promo);
    }

    private String registerVerifyLogin(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!", "name", name))))
                .andExpect(status().isCreated());
        String verifyToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow(() -> new AssertionError("No mail for " + email));
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!Password1!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private UUID firstWorkspaceId(String token) throws Exception {
        JsonNode list = readJson(mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(list.path("items").get(0).path("id").asText());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
