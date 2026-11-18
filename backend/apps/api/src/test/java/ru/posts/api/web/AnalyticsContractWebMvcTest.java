package ru.posts.api.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.api.analytics.AnalyticsService;
import ru.posts.api.error.ApiExceptionHandler;
import ru.posts.api.web.dto.analytics.AnalyticsDtos;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@WebMvcTest(controllers = AnalyticsController.class)
@Import(ApiExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsContractWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void analyticsPostsGetReturnsYearPayload() throws Exception {
        UUID userId = UUID.fromString("019a0000-0002-7000-8000-000000000001");
        UUID workspaceId = UUID.fromString("019a0000-0002-7000-8000-000000000002");
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        List<AnalyticsDtos.PostsAnalyticsMonth> months = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new AnalyticsDtos.PostsAnalyticsMonth(
                        m,
                        m == 11 ? 3 : 0,
                        m == 11 ? 1 : 0,
                        m == 11 ? 1 : 0,
                        m == 11 ? List.of(new AnalyticsDtos.NetworkPublicationStats("vk", 2, 1)) : List.of(),
                        m == 11
                                ? List.of(new AnalyticsDtos.TopPublicationError(
                                        "rate_limited",
                                        "VK rate limit",
                                        1,
                                        workspaceId,
                                        workspaceId,
                                        Instant.parse("2026-11-15T09:00:00Z"),
                                        "vk",
                                        "/api/v1/publications/" + workspaceId + "/retry"))
                                : List.of(),
                        m == 11
                                ? new AnalyticsDtos.AdRevenue(
                                        new BigDecimal("120.00"),
                                        new BigDecimal("100.00"),
                                        new BigDecimal("20.00"),
                                        new BigDecimal("3.60"))
                                : new AnalyticsDtos.AdRevenue(zero, zero, zero, zero),
                        Instant.parse("2026-11-18T12:00:00Z")))
                .toList();
        when(analyticsService.getPostsAnalytics(eq(userId), eq(workspaceId), eq(2026)))
                .thenReturn(new AnalyticsDtos.PostsAnalyticsResponse(
                        workspaceId, 2026, "Europe/Moscow", months, Instant.parse("2026-11-18T12:00:00Z")));

        try (MockedStatic<SecurityAuthSupport> mocked = Mockito.mockStatic(SecurityAuthSupport.class)) {
            mocked.when(SecurityAuthSupport::requirePrincipal).thenReturn(new PostsPrincipal(userId, null));
            mockMvc.perform(get("/api/v1/analytics/posts")
                            .param("workspaceId", workspaceId.toString())
                            .param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.year").value(2026))
                    .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                    .andExpect(jsonPath("$.months.length()").value(12))
                    .andExpect(jsonPath("$.months[10].regularCount").value(3))
                    .andExpect(jsonPath("$.months[10].revenue.fee3Pct").value(3.60));
        }
    }
}
