# Chaos-сценарии TC-PUB (QA-5 / QA-8)

Быстрые аналоги в Testcontainers: `backend/apps/publisher` (`TcPubExtendedIT`, `PipelineIntegrationTest`).
Полный хаос на staging / compose profile `full` — после BE-10 и совместно с Devops (QA-8).

| TC | Локальный прототип | Nightly / staging |
|---|---|---|
| TC-PUB-07 | IT: stale lock + reaper + idempotency | `scripts/kill-publisher.sh` против `publisher-tg` |
| TC-PUB-08 | IT: concurrent SKIP LOCKED + reaper | два реплики scheduler + ShedLock |
| TC-PUB-09 | **ожидает BE-10** | stop RabbitMQ 5 мин → start → сверка эмулятор |
| TC-PUB-10 | **ожидает BE-10** | failover PG (managed) в пике L2 |

## Локальный скрипт (compose full)

```bash
# требует: make up-full, эмулятор, scheduler, publisher-tg
./tests/chaos/scripts/kill-publisher.sh
```

Критерий: после восстановления `GET /_control/verify` → `ok=true`, число постов в эмуляторе = число `publications.state=succeeded`.
