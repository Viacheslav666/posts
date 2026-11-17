package ru.posts.publisher.pipeline;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TC-PUB cases that require BE-10 (prod-quality pipeline), multi-network publishers, WS live
 * statuses, or full chaos/DR infrastructure. Documented as «ожидает BE-10» in {@code
 * docs/qa/test-cases/E5.md}; kept as skipped stubs for traceability.
 */
@Tag("awaits-be10")
class TcPubAwaitingBe10IT {

    @Test
    @Disabled("ожидает BE-10: полный хаос RabbitMQ down 5 мин + уведомление о задержке > 5 мин")
    @DisplayName("TC-PUB-09: RabbitMQ unavailable 5 min — no losses after recovery")
    void tcPub09RabbitMqOutage() {
        // Requires compose/chaos mesh + notifications (BE-10 / BE-11).
    }

    @Test
    @Disabled("ожидает BE-10: failover PostgreSQL в пике (совместно Devops / QA-8)")
    @DisplayName("TC-PUB-10: PostgreSQL failover during peak — 0 losses, 0 duplicates")
    void tcPub10PostgresFailover() {
        // Staging HA PG + load; not runnable on local Testcontainers prototype.
    }

    @Test
    @Disabled("ожидает BE-10: изоляция сетей — publisher-vk + publisher-tg (сейчас только TG-адаптер в прототипе)")
    @DisplayName("TC-PUB-11: VK down 30 min — TG still meets SLA")
    void tcPub11NetworkIsolation() {
        // Needs wired VK publisher deployment and separate queues (BE-10).
    }

    @Test
    @Disabled("ожидает BE-10 / BE-9: edit/pause API near fire time (posts domain + cancel race)")
    @DisplayName("TC-PUB-12: edit −10s / pause −1s before publication")
    void tcPub12EditPauseNearFire() {
        // Cancel/deferred covered partially in TcPubExtendedIT; full AC needs posts API.
    }

    @Test
    @Disabled("ожидает BE-10 / FE: STOMP/WS live publication statuses ≤ 3 s")
    @DisplayName("TC-PUB-13: UI statuses via WebSocket ≤ 3s")
    void tcPub13WebsocketStatus() {
        // Needs WS fan-out from publisher + e2e Playwright.
    }
}
