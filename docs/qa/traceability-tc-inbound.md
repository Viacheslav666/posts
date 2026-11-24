# Матрица трассировки TC-IN → автотест (QA-14 / BE-32)

Обновлено: 2026-11-19.  
Источник требований: p2 **BE-32 «Приёмка»** (каждая строка ниже - дословный пункт).

| Пункт приёмки BE-32 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| IT: `ensure` VK регистрирует сервер; `confirmation` проходит | TC-IN-01 | `backend/apps/api/src/integrationTest/java/ru/posts/api/inbound/SocialInboundIT#vkEnsureConfirmationAndDedup` | CI + nightly | авто |
| Событие, повторённое VK 3 раза → handler **ровно 1 раз** | TC-IN-02 | `SocialInboundIT#vkEnsureConfirmationAndDedup`; `backend/libs/social-inbound/src/integrationTest/java/ru/posts/social/inbound/InboundDedupAndAlbumIntegrationTest` | CI | авто |
| Неверный `secret` → **401**, в очередь не попадает | TC-IN-03 | `SocialInboundIT#vkEnsureConfirmationAndDedup` (негативный secret) | CI | авто |
| Альбом TG 3 фото → **одно** событие, **3** вложения | TC-IN-04 | `SocialInboundIT#telegramAlbumAndFanOut`; `InboundDedupAndAlbumIntegrationTest` (album store) | CI | авто |
| Один TG-чат в **двух** workspace → **два** события | TC-IN-05 | `SocialInboundIT#telegramAlbumAndFanOut` | CI | авто |
| Max: подписка с секретом; `comment_created` нормализуется | TC-IN-06 | `SocialInboundIT#maxCommentAndAuth` | CI | авто |
| Max: неверный секрет → **401** | TC-IN-07 | `SocialInboundIT#maxCommentAndAuth` | CI | авто |
| Chaos: RabbitMQ недоступен → **503**; после восстановления повтор обработан, **потерь 0** | TC-IN-08 | `backend/apps/api/src/integrationTest/java/ru/posts/api/qa14/Qa14InboundChaosIT#rabbitUnavailableThenRecover` (`rabbitmqctl stop_app`, очередь переживает `start_app`). Рестарт процесса `posts-ingest` не поднимается | nightly | **частично** |
| Нагрузка **2000** событий/с, p99 хука **&lt; 200 мс**, без потерь | TC-IN-09 | harness `tests/load/scenarios/l3-social-inbound-hooks.js`. Класса `Qa14InboundLoadSmokeIT` нет. p99 full не измерялся | nightly smoke только при `CHANNEL_ID`+`VK_SECRET` | **нет замера** |
| В коде и логах **нет** хардкода секретов | TC-IN-10 | Ревью + `grep` в CI docs; конфиг через `secret_enc` | PR | ручной+авто |
| ArchUnit: `/_control/**` только профиль **emulator** | TC-IN-10 | `ProdProfileNoControlPathTest`. Класса `Qa14InboundArchIT` нет | CI | **частично** |
| IDOR: чужой канал → **404** | TC-IN-11 | `SocialInboundIT#eventSubscriptionIdor` | CI | авто |
| `RbacMatrixDocsSyncTest` зелёный | TC-IN-12 | `backend/libs/security/src/test/java/ru/posts/security/rbac/RbacMatrixDocsSyncTest` | CI | авто |
| OpenAPI-diff | TC-IN-12 | `./gradlew :apps:api:generateOpenApiDocs` + contract | CI | авто |
| squawk зелёный (V26) | TC-IN-12 | CI flyway/squawk job | CI | авто |

## Вспомогательные автотесты

| Путь | Назначение |
|---|---|
| `backend/libs/social-api/src/test/java/ru/posts/social/api/SocialLiveApiGuardTest` | Guard prod social hosts в тестах |
| `tests/social-emulator` - `InboundInjectController` | Инъекция callback/update |
| `RecordingSocialEventHandler` | Подсчёт доставок handler в IT |

## Запуск

```bash
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.inbound.*'
cd backend && ./gradlew :libs:social-inbound:integrationTest

# Nightly (chaos + load)
./gradlew :apps:api:integrationTest -PincludeNightly=true --tests 'Qa14InboundChaosIT'
PROFILE=full CHANNEL_ID=... VK_SECRET=... k6 run tests/load/scenarios/l3-social-inbound-hooks.js
```