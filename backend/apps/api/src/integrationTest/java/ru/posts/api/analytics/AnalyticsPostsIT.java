package ru.posts.api.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.analytics.AnalyticsPostsAggregator;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.OrdContractEntity;
import ru.posts.persistence.entity.OrdContractorEntity;
import ru.posts.persistence.entity.OrdInvoiceEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.OrdContractRepository;
import ru.posts.persistence.repository.OrdContractorRepository;
import ru.posts.persistence.repository.OrdInvoiceRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * BE-30: materialised analytics aggregates — counts, ORD money (BigDecimal / HALF_UP), Moscow month
 * boundaries, RBAC IDOR, incremental idempotency, volume smoke.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AnalyticsPostsIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    OrdContractorRepository contractorRepository;

    @Autowired
    OrdContractRepository contractRepository;

    @Autowired
    OrdInvoiceRepository invoiceRepository;

    @Autowired
    AnalyticsPostsAggregator aggregator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    @DisplayName("BE-30: aggregates counts, networks, top errors, ORD money; Moscow month boundary")
    void aggregatesAndMoneyAndTimezoneBoundary() throws Exception {
        String token = registerVerifyLogin("be30-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");

        // Publications partitions exist for 2026-10..2027-09 — use November/October 2026.
        YearMonth november = YearMonth.of(2026, 11);
        Instant insideNov = november.atDay(15).atTime(12, 0).atZone(MOSCOW).toInstant();
        // Last instant of October Moscow → must NOT count in November
        Instant lastOctMoscow =
                november.atDay(1).atStartOfDay(MOSCOW).toInstant().minusNanos(1_000_000);
        // First instant of November Moscow → counts
        Instant firstNovMoscow = november.atDay(1).atStartOfDay(MOSCOW).toInstant();

        PostEntity regular = seedPost(workspaceId, false, null);
        PostEntity ad = seedPost(workspaceId, true, null);
        PostEntity repost = seedPost(workspaceId, false, Map.of("network", "vk", "externalId", "wall-1_2"));

        seedPublication(workspaceId, regular.getId(), channel.getId(), "vk", "succeeded", insideNov, null, null);
        seedPublication(workspaceId, ad.getId(), channel.getId(), "tg", "succeeded", firstNovMoscow, null, null);
        seedPublication(workspaceId, repost.getId(), channel.getId(), "ok", "succeeded", insideNov, null, null);
        seedPublication(
                workspaceId,
                regular.getId(),
                channel.getId(),
                "vk",
                "failed_retryable",
                insideNov,
                "rate_limited",
                "VK rate limit");
        seedPublication(
                workspaceId,
                regular.getId(),
                channel.getId(),
                "vk",
                "failed_permanent",
                insideNov,
                "rate_limited",
                "VK rate limit");
        // Boundary: October publication must not appear in November
        seedPublication(workspaceId, regular.getId(), channel.getId(), "vk", "succeeded", lastOctMoscow, null, null);

        OrdContractEntity contract = seedContract(workspaceId, new BigDecimal("120.00"), "20");
        seedInvoice(workspaceId, contract.getId(), 2026, 11, new BigDecimal("120.00"));
        // Soft-deleted invoice must be ignored
        OrdInvoiceEntity deleted = seedInvoice(workspaceId, contract.getId(), 2026, 11, new BigDecimal("999.00"));
        deleted.setDeletedAt(Instant.now());
        deleted.setExternalId("invoice-deleted-" + UUID.randomUUID());
        invoiceRepository.save(deleted);

        aggregator.recomputeWorkspaceYear(workspaceId, 2026);

        JsonNode body = readJson(mockMvc.perform(get("/api/v1/analytics/posts")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.months").isArray())
                .andExpect(jsonPath("$.months.length()").value(12))
                .andReturn());

        JsonNode nov = body.get("months").get(10); // 0-based → month 11
        assertThat(nov.get("month").asInt()).isEqualTo(11);
        assertThat(nov.get("regularCount").asLong()).isEqualTo(3); // 1 succeeded + 2 failed (same post)
        assertThat(nov.get("adCount").asLong()).isEqualTo(1);
        assertThat(nov.get("repostCount").asLong()).isEqualTo(1);
        assertThat(nov.get("revenue").get("amountWithVat").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(nov.get("revenue").get("amountWithoutVat").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(nov.get("revenue").get("vatAmount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(nov.get("revenue").get("fee3Pct").decimalValue()).isEqualByComparingTo("3.60");
        assertThat(nov.get("topErrors")).isNotEmpty();
        assertThat(nov.get("topErrors").get(0).get("errorCode").asText()).isEqualTo("rate_limited");
        assertThat(nov.get("topErrors").get(0).get("count").asLong()).isEqualTo(2);
        assertThat(nov.get("topErrors").get(0).get("retryPath").asText())
                .startsWith("/api/v1/publications/")
                .endsWith("/retry");

        JsonNode oct = body.get("months").get(9);
        assertThat(oct.get("regularCount").asLong()).isEqualTo(1);

        // Idempotent recompute
        aggregator.recomputeMonth(workspaceId, 2026, 11);
        JsonNode again = readJson(mockMvc.perform(get("/api/v1/analytics/posts")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(again.get("months").get(10).get("revenue").get("fee3Pct").decimalValue())
                .isEqualByComparingTo("3.60");
    }

    @Test
    @DisplayName("BE-30: RBAC — foreign workspace → 404; unauthenticated → 401")
    void rbacIdorAndAuth() throws Exception {
        String tokenA = registerVerifyLogin("be30-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        String tokenB = registerVerifyLogin("be30-b-" + UUID.randomUUID() + "@example.com", "Owner B");
        UUID wsA = firstWorkspaceId(tokenA);

        mockMvc.perform(get("/api/v1/analytics/posts")
                        .header("Authorization", bearer(tokenB))
                        .param("workspaceId", wsA.toString())
                        .param("year", "2026"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/analytics/posts")
                        .param("workspaceId", wsA.toString())
                        .param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BE-30: incremental job discovers dirty months and upserts idempotently")
    void incrementalJob() throws Exception {
        String token = registerVerifyLogin("be30-inc-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        PostEntity post = seedPost(workspaceId, false, null);
        Instant runAt =
                YearMonth.of(2026, 12).atDay(10).atTime(9, 0).atZone(MOSCOW).toInstant();
        seedPublication(workspaceId, post.getId(), channel.getId(), "vk", "succeeded", runAt, null, null);

        var first = aggregator.runIncremental();
        assertThat(first.monthsRecomputed()).isGreaterThanOrEqualTo(1);
        assertThat(aggregator.loadYear(workspaceId, 2026)).isNotEmpty();

        var second = aggregator.runIncremental();
        // Watermark advanced past the publication; second run may recompute 0 dirty months
        assertThat(second.monthsRecomputed()).isGreaterThanOrEqualTo(0);
        assertThat(aggregator.loadYear(workspaceId, 2026).stream()
                        .filter(m -> m.month() == 12)
                        .findFirst()
                        .orElseThrow()
                        .regularCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("BE-30: volume smoke — recompute 500 publications within budget (PR CI)")
    void volumeRecomputeBudgetSmoke() throws Exception {
        String token = registerVerifyLogin("be30-vol-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        PostEntity post = seedPost(workspaceId, false, null);
        Instant base =
                YearMonth.of(2026, 10).atDay(1).atTime(10, 0).atZone(MOSCOW).toInstant();
        bulkSeedPublications(workspaceId, post.getId(), channel.getId(), base, 500);
        long t0 = System.nanoTime();
        aggregator.recomputeMonth(workspaceId, 2026, 10);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(elapsedMs).as("500 pubs recompute elapsedMs=%s", elapsedMs).isLessThan(10_000L);
        System.out.println("BE-30_500_pubs_recompute_ms=" + elapsedMs);

        mockMvc.perform(get("/api/v1/analytics/posts")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[9].regularCount").value(500));
    }

    @Test
    @Tag("nightly")
    @DisplayName("BE-30: volume — recompute 5_000 publications within budget (nightly)")
    void volumeRecomputeBudgetNightly() throws Exception {
        String token = registerVerifyLogin("be30-vol5k-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = firstWorkspaceId(token);
        ChannelEntity channel = seedChannel(workspaceId, "vk");
        PostEntity post = seedPost(workspaceId, false, null);
        Instant base =
                YearMonth.of(2026, 10).atDay(1).atTime(10, 0).atZone(MOSCOW).toInstant();
        bulkSeedPublications(workspaceId, post.getId(), channel.getId(), base, 5_000);
        long t0 = System.nanoTime();
        aggregator.recomputeMonth(workspaceId, 2026, 10);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(elapsedMs).as("5k pubs recompute elapsedMs=%s", elapsedMs).isLessThan(30_000L);
        System.out.println("BE-30_5k_pubs_recompute_ms=" + elapsedMs);

        mockMvc.perform(get("/api/v1/analytics/posts")
                        .header("Authorization", bearer(token))
                        .param("workspaceId", workspaceId.toString())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[9].regularCount").value(5_000));
    }

    /** JDBC batch insert — avoids O(n) Hibernate sessions under CI memory pressure. */
    private void bulkSeedPublications(UUID workspaceId, UUID postId, UUID channelId, Instant base, int count) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO publications (
                  id, run_at, workspace_id, post_id, variant_id, channel_id, network,
                  idempotency_key, state, attempts, error_code, error_message, published_at, created_at, meta
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, CAST('{}' AS jsonb))
                """,
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        boolean failed = i % 17 == 0;
                        Instant runAt = base.plusSeconds(i);
                        ps.setObject(1, UuidV7.generate());
                        ps.setTimestamp(2, java.sql.Timestamp.from(runAt));
                        ps.setObject(3, workspaceId);
                        ps.setObject(4, postId);
                        ps.setObject(5, UuidV7.generate());
                        ps.setObject(6, channelId);
                        ps.setString(7, i % 2 == 0 ? "vk" : "tg");
                        ps.setString(8, "be30-bulk-" + UUID.randomUUID());
                        ps.setString(9, failed ? "failed_retryable" : "succeeded");
                        ps.setString(10, failed ? "timeout" : null);
                        ps.setString(11, failed ? "timeout" : null);
                        ps.setTimestamp(12, failed ? null : java.sql.Timestamp.from(runAt));
                        ps.setTimestamp(13, java.sql.Timestamp.from(Instant.now()));
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
    }

    private PostEntity seedPost(UUID workspaceId, boolean ad, Map<String, Object> repost) {
        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(workspaceId);
        post.setTitle("analytics post");
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("text", "hello");
        if (repost != null) {
            content.put("repost", repost);
        }
        post.setBaseContent(content);
        post.setAd(ad);
        post.setStatus("published");
        post.setTimezone("Europe/Moscow");
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        return postRepository.save(post);
    }

    private void seedPublication(
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            String network,
            String state,
            Instant runAt,
            String errorCode,
            String errorMessage) {
        PublicationEntity pub = new PublicationEntity();
        pub.setId(UuidV7.generate());
        pub.setRunAt(runAt);
        pub.setWorkspaceId(workspaceId);
        pub.setPostId(postId);
        pub.setVariantId(UuidV7.generate());
        pub.setChannelId(channelId);
        pub.setNetwork(network);
        pub.setIdempotencyKey("be30-" + UUID.randomUUID());
        pub.setState(state);
        pub.setErrorCode(errorCode);
        pub.setErrorMessage(errorMessage);
        if ("succeeded".equals(state)) {
            pub.setPublishedAt(runAt);
            pub.setRemotePostId("r-" + UUID.randomUUID());
        }
        pub.setCreatedAt(Instant.now());
        publicationRepository.save(pub);
    }

    private OrdContractEntity seedContract(UUID workspaceId, BigDecimal amount, String vatRate) {
        OrdContractorEntity contractor = new OrdContractorEntity();
        contractor.setId(UuidV7.generate());
        contractor.setWorkspaceId(workspaceId);
        contractor.setName("Advertiser");
        contractor.setInn("7707083893");
        contractor.setKpp("770701001");
        contractor.setOrgType("juridical");
        contractor.setCreatedAt(Instant.now());
        contractor.setUpdatedAt(Instant.now());
        contractorRepository.save(contractor);

        OrdContractEntity contract = new OrdContractEntity();
        contract.setId(UuidV7.generate());
        contract.setWorkspaceId(workspaceId);
        contract.setContractorId(contractor.getId());
        contract.setProvider("vk_ord");
        contract.setExternalId("c-" + UUID.randomUUID());
        contract.setAmount(amount);
        contract.setVatRate(vatRate);
        contract.setDateStart(LocalDate.of(2026, 1, 1));
        contract.setStatus("synced");
        contract.setCreatedAt(Instant.now());
        contract.setUpdatedAt(Instant.now());
        return contractRepository.save(contract);
    }

    private OrdInvoiceEntity seedInvoice(UUID workspaceId, UUID contractId, int year, int month, BigDecimal amount) {
        OrdInvoiceEntity invoice = new OrdInvoiceEntity();
        invoice.setId(UuidV7.generate());
        invoice.setWorkspaceId(workspaceId);
        invoice.setProvider("vk_ord");
        invoice.setExternalId("invoice:" + UUID.randomUUID());
        invoice.setPeriodYear(year);
        invoice.setPeriodMonth(month);
        invoice.setContractId(contractId);
        invoice.setAmount(amount);
        invoice.setStatus("submitted");
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        return invoiceRepository.save(invoice);
    }

    private ChannelEntity seedChannel(UUID workspaceId, String network) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork(network);
        channel.setExternalId("ext-" + UUID.randomUUID());
        channel.setType("group");
        channel.setTitle("Test " + network);
        channel.setStatus("active");
        channel.setTimezone("Europe/Moscow");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
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
