package ru.posts.suggest.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Result of {@link SuggestFilterEngine#evaluate}. */
public record SuggestDecision(SuggestAction action, List<SuggestReason> reasons) {

    public SuggestDecision {
        Objects.requireNonNull(action, "action");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static SuggestDecision publish() {
        return new SuggestDecision(SuggestAction.PUBLISH, List.of());
    }

    public static SuggestDecision publish(SuggestReason reason) {
        return new SuggestDecision(SuggestAction.PUBLISH, List.of(reason));
    }

    public static SuggestDecision reject(SuggestReason reason) {
        return new SuggestDecision(SuggestAction.REJECT, List.of(reason));
    }

    public static SuggestDecision reject(List<SuggestReason> reasons) {
        return new SuggestDecision(SuggestAction.REJECT, reasons);
    }

    public static SuggestDecision filterOut(SuggestReason reason) {
        return new SuggestDecision(SuggestAction.FILTER_OUT, List.of(reason));
    }

    public static SuggestDecision filterOut(List<SuggestReason> reasons) {
        return new SuggestDecision(SuggestAction.FILTER_OUT, reasons);
    }

    public boolean passed() {
        return action == SuggestAction.PUBLISH;
    }

    public boolean isReject() {
        return action == SuggestAction.REJECT;
    }

    public boolean isFilterOut() {
        return action == SuggestAction.FILTER_OUT;
    }

    public List<String> reasonCodes() {
        List<String> codes = new ArrayList<>(reasons.size());
        for (SuggestReason reason : reasons) {
            codes.add(reason.code());
        }
        return codes;
    }

    public String primaryCode() {
        return reasons.isEmpty() ? null : reasons.getFirst().code();
    }
}
