# Матрица трассировки TC-PUB → автотест (QA-5 / BE-10)

Обновлено: 2026-11-18. Сети в CI: **Telegram** (+ social-emulator); изоляция VK очереди — без consumer.  
Инвариант **0 потерь / 0 дублей**: сверка `publications.state=succeeded` ↔ `GET /_control/posts/count` + `GET /_control/verify`.

| TC | Автотест | Набор | Статус |
|---|---|---|---|
| TC-PUB-01 | `PipelineLoadIT#tenThousandPublicationsMeetSlo` | nightly (`@Tag("nightly")`) | авто |
| TC-PUB success (happy path) | `TcPubExtendedIT#tcPubSuccessHappyPath` | CI | авто |
| TC-PUB-02 | `PipelineIntegrationTest#tcPub02RetryThenSuccess` | CI | авто |
| TC-PUB hang/timeout | `TcPubExtendedIT#tcPubTimeoutThenSuccess` | CI | авто |
| TC-PUB-03 | `PipelineIntegrationTest#tcPub03LostResponseNoDuplicate` | CI | авто |
| TC-PUB-04 | `TcPubExtendedIT#tcPub04RateLimitRetryAfter` | CI | авто |
| TC-PUB-05 | `TcPubExtendedIT#tcPub05PermanentFailure` | CI | авто |
| TC-PUB-06 | `PipelineIntegrationTest#tcPub06DuplicateDelivery` | CI | авто |
| TC-PUB-07 | `PipelineIntegrationTest#tcPub07PublisherCrashRecovery` + `tests/chaos/scripts/kill-publisher.sh` | CI + chaos | авто |
| TC-PUB-08 | `TcPubExtendedIT#tcPub08DualSchedulerNoDoubleDispatch`, `#schedulerRestartReaperNoLossNoDuplicate` | CI | авто |
| Rate limit (Bucket4j path) | `TcPubExtendedIT#rateLimitDenyThenSuccess` | CI | авто |
| Circuit breaker | `TcPubExtendedIT#circuitBreakerOpenThenRecover` | CI | авто |
| Отложенная публикация | `TcPubExtendedIT#deferredPublicationNotDispatched` | CI | авто |
| Отмена | `TcPubExtendedIT#cancelledPublicationSkipped` | CI | авто |
| TC-PUB-09 | `TcPubBe10IT#tcPub09BrokerOutageRecovery` | CI | авто (BE-10) |
| TC-PUB-10 | `TcPubAwaitingBe10IT#tcPub10PostgresFailover` | staging | **QA-8 / HA PG** |
| TC-PUB-11 | `TcPubBe10IT#tcPub11NetworkIsolation` | CI | авто (BE-10) |
| TC-PUB-12 | `TcPubBe10IT#tcPub12EditPauseNearFire` | CI | авто (BE-10) |
| TC-PUB-13 | `TcPubBe10IT#tcPub13OutboxForWebsocket` | CI | авто (outbox ≤ 3 с; UI — FE) |
| scheduled_actions | `TcPubBe10IT#scheduledActionsFirstCommentAndDelete` | CI | авто (BE-10) |
| reauth_required | `TcPubBe10IT#authMarksChannelReauth` | CI | авто (BE-10) |

## Запуск

```bash
# CI (без load)
cd backend && ./gradlew :apps:publisher:integrationTest

# Nightly (+ TC-PUB-01 10k)
cd backend && ./gradlew :apps:publisher:integrationTest -PincludeNightly=true
```
