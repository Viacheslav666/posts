# Матрица трассировки TC-XP → автотест (QA-14 / BE-36)

Обновлено: 2026-11-19.  
Инварианты петель: **0 обратных публикаций**, **0 дублей** на приёмник; L1–L7 покрыты unit + IT.

| Пункт приёмки BE-36 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| VK (текст + **3 фото**) → по **1** публикации в TG, OK, Max с медиа и форматированием | TC-XP-01 | `Qa14CrosspostPairsIT` | nightly | авто |
| Альбом TG **3 фото** → **1** пост VK с **3** фото | TC-XP-02 | `Qa14CrosspostAlbumIT`; `SocialInboundIT#telegramAlbumAndFanOut` | nightly | авто |
| **Max → TG** | TC-XP-03 | `Qa14CrosspostPairsIT` | nightly | авто |
| TG текст **&gt;1024** + фото → медиа + текст, **порядок** верный | TC-XP-04 | `Qa14CrosspostLongTextIT` | nightly | авто |
| **Петли:** A↔B активны, 1 пост в A → **1** в B, **0** обратно в A | TC-XP-05 | `Qa14CrosspostLoopsIT` | nightly | авто |
| Петля при **задержке коммита реестра** (гонка, **L2**) | TC-XP-06 | `Qa14CrosspostRegistryRaceIT` | nightly | авто |
| **Треугольник** A→B→C→A → по 1 в B и C, **0** в A | TC-XP-07 | `Qa14CrosspostLoopsIT` | nightly | авто |
| **A→C** и **B→C** (B копия из A) → в C **одна** копия (**L4**) | TC-XP-08 | `Qa14CrosspostDedupIT`; `backend/libs/crosspost/src/test/java/ru/posts/crosspost/loop/LoopGuardAndGraphTest` | CI + nightly | авто |
| Повтор события **×5** → **одна** публикация на приёмник | TC-XP-09 | `Qa14CrosspostIdempotencyIT` | nightly | авто |
| `UNKNOWN_OUTCOME` публикатора → **0 дублей** | TC-XP-10 | `Qa14CrosspostReconcileIT`; паттерн `Be33PublishSuggestedReconcileTest` | nightly | авто |
| **Автовыключатель** (L6) срабатывает | TC-XP-11 | `Qa14CrosspostAutoPauseIT` | nightly | авто |
| Рекламный пост (**erid**) пропущен, `ad_post_excluded` | TC-XP-12 | `Qa14CrosspostAdSkipIT` | nightly | авто |
| TG файл **&gt;20 МБ** без Local Bot API → `skipped_unsupported_media` | TC-XP-13 | `Qa14CrosspostOversizeMediaIT` | nightly | авто |
| Пост через Posts в A **не** переносится (default); опубликованная **suggest** — по опции | TC-XP-14 | `LoopGuardAndGraphTest`; `Qa14CrosspostOwnPostIT` | CI + nightly | авто |
| Unit фильтров и трансформаций **≥40** | TC-XP-15 | `CrosspostFilterEngineTest`; `CrosspostTransformerTest`; `CrosspostTextSignalsTest`; `CrosspostScheduleTest` | CI | авто |
| **IDOR** и **RBAC** | TC-XP-16 | `CrosspostServiceIdorTest`; `CrosspostLiveApiGuardTest`; `RbacEndpointMatrixIT` | CI | авто |
| Нагрузка: **1000** постов/мин × **3** приёмника — дублей **0**, петель **0**, p95 без delay **&lt; 60 с** | TC-XP-17 | `Qa14CrosspostLoadIT`; `tests/load/scenarios/l6-crosspost-fanout.js` | nightly `PROFILE=full` | авто |

## Уровни LoopGuard (L1–L7)

| Слой | Unit | IT |
|---|---|---|
| L1 own registry | `LoopGuardAndGraphTest#l1OwnPostSkipped` | `Qa14CrosspostOwnPostIT` |
| L2 intent + 15s | `LoopGuardAndGraphTest#l2IntentRace` | `Qa14CrosspostRegistryRaceIT` |
| L3 lineage | `LoopGuardAndGraphTest#l3HopCopyNotSource` | `Qa14CrosspostLoopsIT` |
| L4 target dedup | `LoopGuardAndGraphTest#l4SimHashDuplicate` | `Qa14CrosspostDedupIT` |
| L5 graph warnings | `CrosspostServiceIdorTest` / API save | `Qa14CrosspostGraphWarnIT` |
| L6 auto_pause | — | `Qa14CrosspostAutoPauseIT` |
| L7 bot identity | `LoopGuardAndGraphTest#l7BotSender` | `Qa14CrosspostPairsIT` (Max) |

## FE (после FE-24)

| Путь | Набор |
|---|---|
| `tests/e2e/specs/automations-crosspost.spec.ts` | CI e2e-smoke (MSW) |

## Запуск

```bash
cd backend && ./gradlew :libs:crosspost:test
./gradlew :apps:api:integrationTest -PincludeNightly=true --tests 'Qa14Crosspost*'
PROFILE=full k6 run tests/load/scenarios/l6-crosspost-fanout.js
```