package ru.posts.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = PersistenceIntegrationTestApp.class)
@Testcontainers
class FlywayMigrationIntegrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "schema_info",
            "users",
            "user_identities",
            "sessions",
            "workspaces",
            "workspace_members",
            "invitations",
            "projects",
            "project_members",
            "social_accounts",
            "channels",
            "channel_sets",
            "channel_set_items",
            "posts",
            "post_variants",
            "media_assets",
            "publications",
            "publication_attempts",
            "scheduled_actions",
            "outbox",
            "audit_log",
            "notifications",
            "idempotency_requests",
            "plans",
            "subscriptions",
            "payments",
            "ledger_entries",
            "usage_counters",
            "analytics_posts_monthly",
            "analytics_posts_job_state",
            "antispam_rules",
            "antispam_word_lists",
            "antispam_log");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesCoreTablesAndPartitions() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                ORDER BY c.relname
                """,
                String.class);

        assertThat(tables).containsAll(EXPECTED_TABLES);

        Boolean publicationsPartitioned = jdbcTemplate.queryForObject(
                """
                SELECT c.relkind = 'p'
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = 'publications'
                """,
                Boolean.class);
        assertThat(publicationsPartitioned).isTrue();

        Integer publicationPartitions = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM pg_inherits i
                JOIN pg_class child ON child.oid = i.inhrelid
                JOIN pg_class parent ON parent.oid = i.inhparent
                WHERE parent.relname = 'publications'
                """,
                Integer.class);
        assertThat(publicationPartitions).isGreaterThanOrEqualTo(6);

        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans", Integer.class);
        assertThat(planCount).isGreaterThanOrEqualTo(4);

        List<Map<String, Object>> dueIndex = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE tablename = 'publications' AND indexname = 'publications_due_idx'
                """);
        assertThat(dueIndex).hasSize(1);
    }
}
