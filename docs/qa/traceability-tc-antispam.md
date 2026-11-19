# Матрица трассировки TC-AS → автотест (QA-14 / BE-35)

Обновлено: 2026-11-19.  
Инварианты: порядок **reply → delete → ban**; идемпотентность inbound ×3; dry-run без remote delete.

| Пункт приёмки BE-35 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| VK: стоп-слово → ответ, удаление, бан **7 дней**; запись в истории | TC-AS-01 | `backend/apps/api/src/integrationTest/java/ru/posts/api/antispam/AntispamProcessorIT#vkStopWordActionsAndIdempotency` | CI | авто |
| Восстановление → комментарий восстановлен, статус **`restored`** | TC-AS-02 | `Qa14AntispamRestoreIT`; `AntispamServiceImpl.restore` + `Be33RemoteActionsVkIT` (restore_comment) | nightly | авто |
| **Dry-run** → только `flagged`, удалений **0** | TC-AS-03 | `AntispamProcessorIT#vkStopWordActionsAndIdempotency` (ветка dry_run) | CI | авто |
| Админ сообщества со стоп-словом **не тронут** | TC-AS-04 | `AntispamEngineTest` (exemptions); `Qa14AntispamAdminSkipIT` | CI | авто |
| Комментарий под **рекламной** записью — только при включённом фильтре | TC-AS-05 | `AntispamEngineTest`; `Qa14AntispamUnderAdsIT` | CI | авто |
| Антиповтор: **3** одинаковых за 12 ч → **2-й и 3-й** удалены | TC-AS-06 | `AntispamEngineTest#repeat`; `AntispamProcessorIT` | CI | авто |
| TG: ссылка в группе обсуждений → **удалено** | TC-AS-07 | `AntispamProcessorIT#tgMaxConstraints`; `Be33RemoteActionsTgIT` | CI | авто |
| TG: `is_automatic_forward` **не тронут** | TC-AS-08 | `AntispamProcessorIT#tgMaxConstraints` | CI | авто |
| TG: старше **48 ч** → `failed` / `tg_delete_window_expired` | TC-AS-09 | `Qa14AntispamTgStaleIT` | nightly | авто |
| TG: `restore` → **422** | TC-AS-09 | `AntispamProcessorIT#tgMaxConstraints` (restore throws) | CI | авто |
| Max: `comment_created` + стоп-слово → удалён; **`restorable=false`** | TC-AS-10 | `AntispamProcessorIT#tgMaxConstraints`; `Be33RemoteActionsMaxIT` | CI | авто |
| Повтор события **×3** → **одно** действие | TC-AS-11 | `AntispamProcessorIT#vkStopWordActionsAndIdempotency` | CI | авто |
| Нормализация: «сп@м», «cпам», «с-п-а-м» ловятся | TC-AS-12 | `backend/libs/antispam/src/test/java/ru/posts/antispam/engine/AntispamEngineTest`; `TextNormalizer` | CI | авто |
| Unit движка **≥40** кейсов | TC-AS-13 | `AntispamEngineTest`; `AntispamActionOrderTest`; `AntispamHistoryScannerTest` | CI | авто |
| Нет прав VK → правило не активируется, **422** с кодом | TC-AS-14 | `Qa14AntispamCapabilityIT` | CI | авто |
| **IDOR** и **RBAC** (INV-RBAC-007) | TC-AS-15 | `RbacEndpointMatrixIT`; `RbacIdorMatrixIT` (antispam ops) | CI | авто |
| Нагрузка: **3000** комм/с, **50 000** каналов, p99 вердикта **&lt; 5 мс**, лимиты токена не нарушены, **потерь 0** | TC-AS-16 | `Qa14AntispamLoadIT`; `tests/load/scenarios/l5-antispam-verdicts.js` | nightly `PROFILE=full` | авто |
| Redis degrade: лимитер fail-safe, **без потерь** | — | `Qa14RedisDegradeTest` | nightly | авто |
| SPI **CONSUMED** блокирует suggest | — | `SuggestEventHandler` order 20 + antispam order 10 IT в `Qa14AntispamConsumesSuggestIT` | nightly | авто |

## Publisher pipeline (BE-33)

| Путь | Назначение |
|---|---|
| `Be33RemoteActionsVkIT` | delete/restore/ban VK |
| `Be33RemoteActionsTgIT` | TG delete |
| `Be33RemoteActionsMaxIT` | Max delete |
| `Be33ModerationLoadIT` | smoke 100 actions/s |

## Запуск

```bash
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.antispam.*'
cd backend && ./gradlew :libs:antispam:test
cd backend && ./gradlew :apps:publisher:integrationTest --tests 'Be33RemoteActions*'
```