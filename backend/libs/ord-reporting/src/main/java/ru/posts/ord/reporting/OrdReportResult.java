package ru.posts.ord.reporting;

import java.util.UUID;

public record OrdReportResult(
        UUID runId,
        String status,
        int invoicesCount,
        int statisticsCount,
        int periodYear,
        int periodMonth,
        String lastError) {}
