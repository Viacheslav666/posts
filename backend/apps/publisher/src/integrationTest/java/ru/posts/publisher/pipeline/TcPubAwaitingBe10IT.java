package ru.posts.publisher.pipeline;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Remaining chaos/DR cases that need staging HA infrastructure (QA-8 / Devops). Implemented BE-10
 * CI coverage lives in {@link TcPubBe10IT}.
 */
@Tag("awaits-chaos")
class TcPubAwaitingBe10IT {

    @Test
    @Disabled("TC-PUB-10: requires Managed PG failover under load (QA-8 / staging HA)")
    @DisplayName("TC-PUB-10: PostgreSQL failover during peak — 0 losses, 0 duplicates")
    void tcPub10PostgresFailover() {
        // Staging HA PG + load; not runnable on local Testcontainers singleton PG.
    }
}
