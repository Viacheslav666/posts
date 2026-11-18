package ru.posts.api.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.analytics.AnalyticsService;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.analytics.AnalyticsDtos;

/** Stub so openapi profile can boot AnalyticsController. */
@Configuration
@Profile("openapi")
public class OpenApiAnalyticsStubConfig {

    @Bean
    AnalyticsService analyticsService() {
        return (userId, workspaceId, year) -> {
            if (workspaceId == null) {
                throw new NotImplementedException("openapi");
            }
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            List<AnalyticsDtos.PostsAnalyticsMonth> months = java.util.stream.IntStream.rangeClosed(1, 12)
                    .mapToObj(m -> new AnalyticsDtos.PostsAnalyticsMonth(
                            m,
                            0,
                            0,
                            0,
                            List.of(),
                            List.of(),
                            new AnalyticsDtos.AdRevenue(zero, zero, zero, zero),
                            null))
                    .toList();
            return new AnalyticsDtos.PostsAnalyticsResponse(
                    workspaceId == null ? UUID.randomUUID() : workspaceId, year, "Europe/Moscow", months, null);
        };
    }
}
