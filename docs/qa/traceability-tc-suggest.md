# Матрица трассировки TC-SUG → автотест (QA-14 / BE-34)

Обновлено: 2026-11-19.  
Инварианты: **0 дублей** на triple callback, bulk и `UNKNOWN_OUTCOME`.

| Пункт приёмки BE-34 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| IT: предложка через Callback → публикация в слот с `signed=1` и первым комментарием | TC-SUG-01 | `backend/apps/api/src/integrationTest/java/ru/posts/api/suggest/SuggestVkIT#callbackPublishSignedAndComment` | CI | авто |
| Стоп-слово → `rejected` + `delete_post` + уведомление (если разрешены сообщения) | TC-SUG-02 | `SuggestVkIT#stopWordRejectDelete` | CI | авто |
| Лимит на автора: **3-я** за сутки → `filtered_out` с причиной | TC-SUG-03 | `SuggestVkIT#authorDayLimit` | CI | авто |
| Callback **×3** → **одна** запись и **одна** публикация | TC-SUG-04, TC-SUG-06 | `SuggestVkIT#tripleCallbackIdempotent` | CI | авто |
| `bulk approve_all` на 500 → каждая **ровно 1 раз**, прогресс **100%** | TC-SUG-05 | `SuggestVkIT#bulkApproveAll` | CI | авто |
| `unknown_outcome` при публикации → **0 дублей** | TC-SUG-10 | `backend/apps/publisher/src/integrationTest/java/ru/posts/publisher/pipeline/Be33PublishSuggestedReconcileTest`; `SuggestRemoteActionReconciler` (unit) | CI / nightly | авто |
| Сверка: удалённая в VK → `removed_externally` | TC-SUG-07 | `SuggestVkIT#syncRemovedExternally` | CI | авто |
| Пауза после рекламного поста соблюдается | TC-SUG-08 | `SuggestVkIT#adsPauseDelaysSlot` | CI | авто |
| Антидубль: текст как пост стены 7 дней → `filtered_out` | TC-SUG-09 | `SuggestVkIT#nearDuplicateFilteredOut` | CI | авто |
| Unit фильтров **≥30** (ё/е, whole word, TZ Moscow/Samara, …) | TC-SUG-10, TC-SUG-11 | `backend/libs/automation-suggest/src/test/java/ru/posts/suggest/filter/SuggestFilterEngineTest` | CI | авто |
| IDOR: чужой канал → **404** | TC-SUG-12 | `SuggestVkIT#idorNotFound`; `SuggestApiIT` | CI | авто |
| RBAC: Author/Viewer не модерируют (**INV-RBAC-007**) | TC-SUG-12 | `SuggestApiIT` | CI | авто |
| Нагрузка: **200** предложенных/с, **10 000** каналов, p95 «событие→решение» &lt; 2 с, **без дублей** | TC-SUG-12 | классов `Qa14SuggestLoadIT` и `tests/load/scenarios/l4-suggest-ingest.js` нет | — | **нет автотеста** |
| Ручной режим / hybrid (очередь модерации) | TC-SUG-04 | `SuggestVkIT#bulkApproveAll` (manual seed); `SuggestApiIT` (mode=manual) | CI | авто |
| Guard: нет live `api.vk.com` | — | `SuggestVkIT#liveApiGuard`; `SocialLiveApiGuardTest` | CI | авто |

## FE (после FE-22)

| TC | Путь | Набор |
|---|---|---|
| UI smoke suggest queue | `tests/e2e/specs/automations-suggest.spec.ts` (MSW) | CI e2e-smoke (план) |

## Запуск

```bash
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.suggest.*'
cd backend && ./gradlew :libs:automation-suggest:test
cd backend && ./gradlew :apps:publisher:integrationTest --tests 'Be33PublishSuggestedReconcileTest'
```