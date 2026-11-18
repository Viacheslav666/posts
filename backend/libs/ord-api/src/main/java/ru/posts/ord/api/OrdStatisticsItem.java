package ru.posts.ord.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One statistics row (creative × platform × period). */
public record OrdStatisticsItem(
        String creativeExternalId,
        String platformExternalId,
        long showsCount,
        LocalDate dateStartActual,
        LocalDate dateEndActual,
        LocalDate dateStartPlanned,
        LocalDate dateEndPlanned,
        BigDecimal amount,
        String payType,
        String invoiceExternalId,
        String contractExternalId) {}
