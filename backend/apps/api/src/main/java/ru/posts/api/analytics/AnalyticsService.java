package ru.posts.api.analytics;

import java.util.UUID;
import ru.posts.api.web.dto.analytics.AnalyticsDtos;

public interface AnalyticsService {

    AnalyticsDtos.PostsAnalyticsResponse getPostsAnalytics(UUID userId, UUID workspaceId, int year);
}
