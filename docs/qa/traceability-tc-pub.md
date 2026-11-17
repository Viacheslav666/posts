# Матрица трассировки TC-PUB → автотест (QA-5)

Обновлено: 2026-11-17. Прототип сетей: **Telegram** (+ social-emulator).  
Инвариант **0 потерь / 0 дублей**: сверка `publications.state=succeeded` ↔ `GET /_control/posts/count` + `GET /_control/verify`.

| TC | Автотест | Набор | Статус |
|---|---|---|---|
| TC-PUB-01 | `PipelineLoadIT#tenThousandPublicationsMeetSlo` | nightly (`@Tag("nightly")`) | авто |
| TC-PUB success (happy path) | `TcPubExtendedIT#tcPubSuccessHappyPath` | CI | авто |
| TC-PUB-02 | `PipelineIntegrationTest#tcPub02RetryThenSuccess` | CI | авто (BE-4) |
| TC-PUB hang/timeout | `TcPubExtendedIT#tcPubTimeoutThenSuccess` | CI | авто |
| TC-PUB-03 | `PipelineIntegrationTest#tcPub03LostResponseNoDuplicate` | CI | авто (BE-4) |
| TC-PUB-04 | `TcPubExtendedIT#tcPub04RateLimitRetryAfter` | CI | авто |
| TC-PUB-05 | `TcPubExtendedIT#tcPub05PermanentFailure` | CI | авто |
| TC-PUB-06 | `PipelineIntegrationTest#tcPub06DuplicateDelivery` | CI | авто (BE-4) |
| TC-PUB-07 | `PipelineIntegrationTest#tcPub07PublisherCrashRecovery` + `tests/chaos/scripts/kill-publisher.sh` | CI + chaos | авто |
| TC-PUB-08 | `TcPubExtendedIT#tcPub08DualSchedulerNoDoubleDispatch`, `#schedulerRestartReaperNoLossNoDuplicate` | CI | авто |
| Rate limit (Bucket4j path) | `TcPubExtendedIT#rateLimitDenyThenSuccess` | CI | авто |
| Circuit breaker | `TcPubExtendedIT#circuitBreakerOpenThenRecover` | CI | авто |
| Отложенная публикация | `TcPubExtendedIT#deferredPublicationNotDispatched` | CI | авто |
| Отмена | `TcPubExtendedIT#cancelledPublicationSkipped` | CI | авто |
| TC-PUB-09 | `TcPubAwaitingBe10IT#tcPub09RabbitMqOutage` | — | **ожидает BE-10** (`@Disabled`) |
| TC-PUB-10 | `TcPubAwaitingBe10IT#tcPub10PostgresFailover` | — | **ожидает BE-10** (`@Disabled`) |
| TC-PUB-11 | `TcPubAwaitingBe10IT#tcPub11NetworkIsolation` | — | **ожидает BE-10** (нужен publisher-vk) |
| TC-PUB-12 | `TcPubAwaitingBe10IT#tcPub12EditPauseNearFire` | — | **ожидает BE-10 / BE-9** (частично: cancel/deferred) |
| TC-PUB-13 | `TcPubAwaitingBe10IT#tcPub13WebsocketStatus` | — | **ожидает BE-10 / FE** |

## Запуск

```bash
# CI (без load)
cd backend && ./gradlew :apps:publisher:integrationTest

# Nightly (+ TC-PUB-01 10k)
cd backend && ./gradlew :apps:publisher:integrationTest -PincludeNightly=true
```
