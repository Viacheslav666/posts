package ru.posts.ord.api;

import java.util.List;

/** Batch statistics submission. */
public record OrdStatisticsRequest(List<OrdStatisticsItem> items) {
    public OrdStatisticsRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
