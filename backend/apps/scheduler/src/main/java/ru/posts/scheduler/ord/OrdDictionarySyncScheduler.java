package ru.posts.scheduler.ord;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monthly ORD dictionary refresh (BE-25). Reloads official KKTU/OKSM seed snapshots shipped in
 * {@code ord/kktu-seed.json} and {@code ord/oksm-seed.json}. Optional HTTP sync can be layered later
 * when a system ORD key is configured; seed files are generated from the ERIR/KKTU classifier.
 */
@Component
public class OrdDictionarySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrdDictionarySyncScheduler.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public OrdDictionarySyncScheduler(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${posts.ord.dictionaries.sync-cron:0 15 4 1 * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordDictionarySync", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void syncDictionaries() {
        int kktu = upsertKktu();
        int oksm = upsertOksm();
        log.info("ORD dictionary sync completed: kktuUpserted={}, oksmUpserted={}", kktu, oksm);
    }

    private int upsertKktu() {
        List<Map<String, Object>> rows = readJson("ord/kktu-seed.json");
        Instant now = Instant.now();
        int n = 0;
        for (Map<String, Object> row : rows) {
            n += jdbcClient
                    .sql(
                            """
                            INSERT INTO ord_dictionary_kktu (
                                code, name, parent_code, active, synced_at, source, updated_at
                            ) VALUES (
                                :code, :name, :parentCode, TRUE, :now, 'erir_kktu_2025', :now
                            )
                            ON CONFLICT (code) DO UPDATE SET
                                name = EXCLUDED.name,
                                parent_code = EXCLUDED.parent_code,
                                active = TRUE,
                                source = EXCLUDED.source,
                                synced_at = EXCLUDED.synced_at,
                                updated_at = EXCLUDED.updated_at
                            """)
                    .param("code", String.valueOf(row.get("code")))
                    .param("name", String.valueOf(row.get("name")))
                    .param("parentCode", row.get("parent_code") == null ? null : String.valueOf(row.get("parent_code")))
                    .param("now", java.sql.Timestamp.from(now))
                    .update();
        }
        return n;
    }

    private int upsertOksm() {
        List<Map<String, Object>> rows = readJson("ord/oksm-seed.json");
        Instant now = Instant.now();
        int n = 0;
        for (Map<String, Object> row : rows) {
            n += jdbcClient
                    .sql(
                            """
                            INSERT INTO ord_dictionary_oksm (
                                code, name, alpha2, active, synced_at, source, updated_at
                            ) VALUES (
                                :code, :name, :alpha2, TRUE, :now, 'iso_3166_1', :now
                            )
                            ON CONFLICT (code) DO UPDATE SET
                                name = EXCLUDED.name,
                                alpha2 = EXCLUDED.alpha2,
                                active = TRUE,
                                source = EXCLUDED.source,
                                synced_at = EXCLUDED.synced_at,
                                updated_at = EXCLUDED.updated_at
                            """)
                    .param("code", String.valueOf(row.get("code")))
                    .param("name", String.valueOf(row.get("name")))
                    .param("alpha2", row.get("alpha2") == null ? null : String.valueOf(row.get("alpha2")))
                    .param("now", java.sql.Timestamp.from(now))
                    .update();
        }
        return n;
    }

    private List<Map<String, Object>> readJson(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read ORD dictionary seed " + classpath, ex);
        }
    }
}
