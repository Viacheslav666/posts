package ru.posts.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/** Partition-aware JDBC access for {@code antispam_log} (BE-35). */
public class AntispamLogJdbc {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AntispamLogJdbc(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record AntispamLogRow(
            UUID id,
            UUID workspaceId,
            UUID channelId,
            UUID ruleId,
            String section,
            String remoteCommentKey,
            String remoteRefJson,
            String authorRef,
            String textSnapshot,
            String attachmentsSummary,
            List<String> reasons,
            String action,
            String state,
            boolean restorable,
            List<UUID> remoteActionIds,
            Instant createdAt,
            UUID restoredBy,
            Instant restoredAt) {}

    /**
     * Inserts a log row. On unique conflict within a partition returns the existing id (idempotent
     * replay). Cross-partition uniqueness is enforced by application lookup first.
     */
    @Transactional
    public UUID insert(AntispamLogRow row) {
        Optional<UUID> existing = findIdByDedup(row.channelId(), row.section(), row.remoteCommentKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.update(
                    """
                    INSERT INTO antispam_log (
                        id, workspace_id, channel_id, rule_id, section, remote_comment_key,
                        remote_ref, author_ref, text_snapshot, attachments_summary, reasons,
                        action, state, restorable, remote_action_ids, created_at, restored_by, restored_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    row.id(),
                    row.workspaceId(),
                    row.channelId(),
                    row.ruleId(),
                    row.section(),
                    row.remoteCommentKey(),
                    row.remoteRefJson() == null ? "{}" : row.remoteRefJson(),
                    row.authorRef(),
                    truncate(row.textSnapshot(), 4096),
                    row.attachmentsSummary(),
                    toTextArray(row.reasons()),
                    row.action(),
                    row.state(),
                    row.restorable(),
                    toUuidArray(row.remoteActionIds()),
                    Timestamp.from(row.createdAt()),
                    row.restoredBy(),
                    row.restoredAt() == null ? null : Timestamp.from(row.restoredAt()));
            return row.id();
        } catch (DuplicateKeyException ex) {
            return findIdByDedup(row.channelId(), row.section(), row.remoteCommentKey())
                    .orElse(row.id());
        }
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByDedup(UUID channelId, String section, String remoteCommentKey) {
        List<UUID> ids = jdbc.query(
                """
                SELECT id FROM antispam_log
                WHERE channel_id = ? AND section = ? AND remote_comment_key = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (rs, i) -> (UUID) rs.getObject("id"),
                channelId,
                section,
                remoteCommentKey);
        return ids.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<AntispamLogRow> findById(UUID id) {
        List<AntispamLogRow> rows = jdbc.query(
                """
                SELECT * FROM antispam_log WHERE id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                ROW_MAPPER,
                id);
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<AntispamLogRow> findByIdAndWorkspace(UUID id, UUID workspaceId) {
        List<AntispamLogRow> rows = jdbc.query(
                """
                SELECT * FROM antispam_log WHERE id = ? AND workspace_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                ROW_MAPPER,
                id,
                workspaceId);
        return rows.stream().findFirst();
    }

    @Transactional
    public void updateState(
            UUID id, Instant createdAt, String state, UUID restoredBy, Instant restoredAt, List<UUID> remoteActionIds) {
        jdbc.update(
                """
                UPDATE antispam_log
                SET state = ?, restored_by = ?, restored_at = ?, remote_action_ids = COALESCE(?, remote_action_ids)
                WHERE id = ? AND created_at = ?
                """,
                state,
                restoredBy,
                restoredAt == null ? null : Timestamp.from(restoredAt),
                remoteActionIds == null ? null : toUuidArray(remoteActionIds),
                id,
                Timestamp.from(createdAt));
    }

    @Transactional
    public void appendRemoteActions(UUID id, Instant createdAt, List<UUID> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            return;
        }
        jdbc.update(
                """
                UPDATE antispam_log
                SET remote_action_ids = remote_action_ids || ?
                WHERE id = ? AND created_at = ?
                """,
                toUuidArray(actionIds),
                id,
                Timestamp.from(createdAt));
    }

    @Transactional(readOnly = true)
    public List<AntispamLogRow> list(
            UUID workspaceId,
            UUID channelId,
            String section,
            UUID ruleId,
            String action,
            Instant from,
            Instant to,
            Instant cursorCreatedAt,
            UUID cursorId,
            int limit) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT * FROM antispam_log
                WHERE workspace_id = ?
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(workspaceId);
        if (channelId != null) {
            sql.append(" AND channel_id = ?");
            args.add(channelId);
        }
        if (section != null && !section.isBlank()) {
            sql.append(" AND section = ?");
            args.add(section);
        }
        if (ruleId != null) {
            sql.append(" AND rule_id = ?");
            args.add(ruleId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND created_at <= ?");
            args.add(Timestamp.from(to));
        }
        if (cursorCreatedAt != null && cursorId != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(Timestamp.from(cursorCreatedAt));
            args.add(cursorId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Transactional(readOnly = true)
    public long countActionsSince(UUID channelId, Instant since) {
        Long n = jdbc.queryForObject(
                """
                SELECT count(*) FROM antispam_log
                WHERE channel_id = ? AND created_at >= ? AND action <> 'flagged'
                """,
                Long.class,
                channelId,
                Timestamp.from(since));
        return n == null ? 0L : n;
    }

    public String toJson(Map<String, ?> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Array toTextArray(List<String> values) {
        String[] arr = values == null ? new String[0] : values.toArray(String[]::new);
        return jdbc.execute((java.sql.Connection conn) -> conn.createArrayOf("text", arr));
    }

    @Transactional(readOnly = true)
    public List<AntispamLogRow> findByStates(List<String> states, int limit) {
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", states.stream().map(s -> "?").toList());
        List<Object> args = new java.util.ArrayList<>(states);
        args.add(limit);
        return jdbc.query(
                "SELECT * FROM antispam_log WHERE state IN (" + placeholders + ") ORDER BY created_at ASC LIMIT ?",
                ROW_MAPPER,
                args.toArray());
    }

    private Array toUuidArray(List<UUID> values) {
        Object[] arr = values == null ? new UUID[0] : values.toArray();
        return jdbc.execute((java.sql.Connection conn) -> conn.createArrayOf("uuid", arr));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static final RowMapper<AntispamLogRow> ROW_MAPPER = (rs, rowNum) -> new AntispamLogRow(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("workspace_id"),
            (UUID) rs.getObject("channel_id"),
            (UUID) rs.getObject("rule_id"),
            rs.getString("section"),
            rs.getString("remote_comment_key"),
            rs.getString("remote_ref"),
            rs.getString("author_ref"),
            rs.getString("text_snapshot"),
            rs.getString("attachments_summary"),
            readTextArray(rs, "reasons"),
            rs.getString("action"),
            rs.getString("state"),
            rs.getBoolean("restorable"),
            readUuidArray(rs, "remote_action_ids"),
            rs.getTimestamp("created_at").toInstant(),
            (UUID) rs.getObject("restored_by"),
            rs.getTimestamp("restored_at") == null
                    ? null
                    : rs.getTimestamp("restored_at").toInstant());

    private static List<String> readTextArray(ResultSet rs, String col) throws SQLException {
        Array arr = rs.getArray(col);
        if (arr == null) {
            return List.of();
        }
        Object raw = arr.getArray();
        if (raw instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        if (raw instanceof Object[] objects) {
            return Arrays.stream(objects).map(String::valueOf).toList();
        }
        return List.of();
    }

    private static List<UUID> readUuidArray(ResultSet rs, String col) throws SQLException {
        Array arr = rs.getArray(col);
        if (arr == null) {
            return List.of();
        }
        Object raw = arr.getArray();
        if (raw instanceof UUID[] uuids) {
            return Arrays.asList(uuids);
        }
        if (raw instanceof Object[] objects) {
            return Arrays.stream(objects)
                    .map(o -> UUID.fromString(String.valueOf(o)))
                    .toList();
        }
        return List.of();
    }
}
