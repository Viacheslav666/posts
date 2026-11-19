package ru.posts.suggest.filter;

import java.util.List;

/** Filter engine outcome for a suggested post. */
public record SuggestDecision(SuggestAction action, List<SuggestReason> reasons) {

    public SuggestDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static SuggestDecision of(SuggestAction action, List<SuggestReason> reasons) {
        return new SuggestDecision(action, reasons);
    }

    public static SuggestDecision publish() {
        return new SuggestDecision(SuggestAction.PUBLISH, List.of());
    }

    public boolean hasSoftReasons() {
        return reasons.stream().anyMatch(SuggestReason::soft);
    }

    public boolean hasHardReject() {
        return reasons.stream().anyMatch(r -> !r.soft());
    }
}
