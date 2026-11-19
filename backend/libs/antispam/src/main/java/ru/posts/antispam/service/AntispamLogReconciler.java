package ru.posts.antispam.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.AntispamLogJdbc.AntispamLogRow;

/** Moves antispam_log out of pending/restored once the remote actions settle. */
public class AntispamLogReconciler {

    private final AntispamLogJdbc logJdbc;
    private final JdbcTemplate jdbc;

    public AntispamLogReconciler(AntispamLogJdbc logJdbc, JdbcTemplate jdbc) {
        this.logJdbc = logJdbc;
        this.jdbc = jdbc;
    }

    public int reconcile(int limit) {
        List<AntispamLogRow> rows = logJdbc.findByStates(List.of("pending", "restored"), Math.max(1, limit));
        int updated = 0;
        for (AntispamLogRow row : rows) {
            String next = nextState(row.state(), actionStates(row.remoteActionIds()));
            if (next == null || next.equals(row.state())) {
                continue;
            }
            logJdbc.updateState(row.id(), row.createdAt(), next, row.restoredBy(), row.restoredAt(), null);
            updated++;
        }
        return updated;
    }

    /** Returns the state to write, or null to leave the row alone. */
    static String nextState(String logState, List<String> actionStates) {
        if (actionStates == null || actionStates.isEmpty()) {
            return null;
        }
        boolean anyPermanent =
                actionStates.stream().anyMatch(state -> "failed_permanent".equals(state) || "cancelled".equals(state));
        boolean allSucceeded = actionStates.stream().allMatch("succeeded"::equals);
        if ("restored".equals(logState)) {
            return anyPermanent ? "restore_failed" : null;
        }
        if ("pending".equals(logState)) {
            if (anyPermanent) {
                return "failed";
            }
            if (allSucceeded) {
                return "done";
            }
        }
        return null;
    }

    private List<String> actionStates(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> states = new ArrayList<>();
        for (UUID id : ids) {
            List<String> found = jdbc.query(
                    "SELECT state FROM social_remote_actions WHERE id = ? ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString("state"),
                    id);
            if (!found.isEmpty()) {
                states.add(found.getFirst());
            }
        }
        return states;
    }
}
