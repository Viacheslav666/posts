# Матрица трассировки TC-AS → автотест (QA-14 / BE-35)

Обновлено: 2026-11-19.  
Инварианты: порядок **reply → delete → ban**; идемпотентность inbound ×3; dry-run без remote delete.

| Пункт приёмки BE-35 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| VK: стоп-слово → ответ, удаление, бан **7 дней**; запись в истории | TC-AS-01 | `AntispamProcessorIT#vkStopWordActionsAndIdempotency` | CI | авто |
| Восстановление → комментарий восстановлен, статус **`restored`** | TC-AS-02 | `Qa14AntispamIT#restoreSetsRestoredStatus` | CI | авто |
| **Dry-run** → только `flagged`, удалений **0** | TC-AS-03 | `AntispamProcessorIT#vkStopWordActionsAndIdempotency` (ветка dry_run) | CI | авто |
| Админ сообщества со стоп-словом **не тронут** | TC-AS-04 | `Qa14AntispamIT#adminAuthorSkipped` | CI | авто |
| Комментарий под **рекламной** записью - только при включённом фильтре | TC-AS-05 | `Qa14AntispamIT#commentUnderAdsRequiresFilter` | CI | авто |
| Антиповтор: **3** одинаковых за 12 ч → **2-й и 3-й** удалены | TC-AS-06 | `AntispamEngineTest#repeat`; `AntispamProcessorIT` | CI | авто |
| TG: ссылка в группе обсуждений → **удалено** | TC-AS-07 | `AntispamProcessorIT#tgMaxConstraints`; `Be33RemoteActionsTgIT` | CI | авто |
| TG: `is_automatic_forward` **не тронут** | TC-AS-08 | `AntispamProcessorIT#tgMaxConstraints` | CI | авто |
| TG: старше **48 ч** → `failed` / `tg_delete_window_expired` | TC-AS-09 | `Qa14AntispamIT#tgStaleCommentFailsWindow` | CI | авто |
| TG: `restore` → **422** | TC-AS-09 | `AntispamProcessorIT#tgMaxConstraints` (restore throws) | CI | авто |
| Max: `comment_created` + стоп-слово → удалён; **`restorable=false`** | TC-AS-10 | `AntispamProcessorIT#tgMaxConstraints` | CI | авто |
| Повтор события **×3** → **одно** действие | TC-AS-11 | `AntispamProcessorIT#vkStopWordActionsAndIdempotency` | CI | авто |
| Нормализация: «сп@м», «cпам», «с-п-а-м» ловятся | TC-AS-12 | `AntispamEngineTest`; `TextNormalizer` | CI | авто |
| Unit движка **≥40** кейсов | TC-AS-13 | `AntispamEngineTest`; `AntispamActionOrderTest`; `AntispamHistoryScannerTest` | CI | авто |
| Нет прав VK → правило не активируется, **422** с кодом | TC-AS-14 | `Qa14AntispamIT#vkActivateWithoutTokenReturns422` | CI | авто |
| **IDOR** и **RBAC** (INV-RBAC-007) | TC-AS-15 | `RbacEndpointMatrixIT`; `RbacIdorMatrixIT` (antispam ops) | CI | авто |
| Bulk suggest **approve_ids/reject_ids** IDOR | TC-SUG-IDOR | `Qa14SuggestBulkIdorIT` | CI | авто |
| Crosspost **erid** skip (смежный BE-36) | TC-XP-12 | `Qa14AntispamIT#crosspostEridExcluded` | CI | авто |
| Нагрузка: **3000** комм/с (хук), p95/p99 latency | TC-AS-16 | `tests/load/scenarios/l4-p2-antispam-events.js` | nightly | **частично** |
| Redis degrade: лимитер fail-safe | - | `Qa14RedisDegradeTest` | CI | **частично** |

## Запуск

```bash
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.antispam.*' --tests 'ru.posts.api.qa14.Qa14AntispamIT'
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.qa14.Qa14SuggestBulkIdorIT'
```