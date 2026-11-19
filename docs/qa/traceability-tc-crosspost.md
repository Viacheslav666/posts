# Матрица трассировки TC-XP → автотест (QA-14 / BE-36)

Обновлено: 2026-11-19.  
Инварианты петель: **0 обратных публикаций**, **0 дублей** на приёмник; L1–L7 покрыты unit + IT.

| Пункт приёмки BE-36 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| VK (текст + **3 фото**) → по **1** публикации в TG, OK, Max с медиа и форматированием | TC-XP-01 | класса `Qa14CrosspostPairsIT` нет | — | **нет автотеста** |
| Альбом TG **3 фото** → **1** пост VK с **3** фото | TC-XP-02 | `SocialInboundIT#telegramAlbumAndFanOut` проверяет только inbound (3 вложения), не публикацию VK | CI | **частично** |
| **Max → TG** | TC-XP-03 | класса `Qa14CrosspostPairsIT` нет | — | **нет автотеста** |
| TG текст **&gt;1024** + фото → медиа + текст, **порядок** верный | TC-XP-04 | класса `Qa14CrosspostLongTextIT` нет | — | **нет автотеста** |
| **Петли:** A↔B активны, 1 пост в A → **1** в B, **0** обратно в A | TC-XP-05 | `Qa14CrosspostLoopsIT#loopAbNoReverse` — блок эха через L1 `own_crosspost`, без прогона publisher и эмулятора | CI | **частично** |
| Петля при **задержке коммита реестра** (гонка, **L2**) | TC-XP-06 | `Qa14CrosspostLoopsIT#registryLagBlockedByIntent` | CI | авто |
| **Треугольник** A→B→C→A → по 1 в B и C, **0** в A | TC-XP-07 | `Qa14CrosspostLoopsIT#triangleSingleCopies` — 0 в A только из заранее записанного реестра (L1), цепочка hop/L3 не проверяется | CI | **частично** |
| **A→C** и **B→C** (B копия из A) → в C **одна** копия (**L4**) | TC-XP-08 | `Qa14CrosspostLoopsIT#fanInOneCopyOnSharedTarget`; `LoopGuardAndGraphTest` | CI | авто |
| Повтор события **×5** → **одна** публикация на приёмник | TC-XP-09 | `Qa14CrosspostLoopsIT#duplicateInboundFiveTimes` | CI | авто |
| `UNKNOWN_OUTCOME` публикатора → **0 дублей** | TC-XP-10 | класса `Qa14CrosspostReconcileIT` нет; `Be33PublishSuggestedReconcileTest` покрывает suggest, не crosspost | — | **нет автотеста** |
| **Автовыключатель** (L6) срабатывает | TC-XP-11 | класса `Qa14CrosspostAutoPauseIT` нет; есть только unit `CircuitBreakerTest` | CI unit | **частично** |
| Рекламный пост (**erid**) пропущен, `ad_post_excluded` | TC-XP-12 | класса `Qa14CrosspostAdSkipIT` нет; есть unit `CrosspostFilterEngineTest` | CI unit | **частично** |
| TG файл **&gt;20 МБ** без Local Bot API → `skipped_unsupported_media` | TC-XP-13 | `Qa14CrosspostLoopsIT#telegramFileOver20MbSkipped` | CI | авто |
| Пост через Posts в A **не** переносится (default); опубликованная **suggest** — по опции | TC-XP-14 | `LoopGuardAndGraphTest` (unit). Класса `Qa14CrosspostOwnPostIT` нет | CI unit | **частично** |
| Unit фильтров и трансформаций **≥40** | TC-XP-15 | `CrosspostFilterEngineTest`; `CrosspostTransformerTest`; `CrosspostTextSignalsTest`; `CrosspostScheduleTest` | CI | авто |
| **IDOR** и **RBAC** | TC-XP-16 | `CrosspostServiceIdorTest`; `CrosspostLiveApiGuardTest`; `RbacEndpointMatrixIT` | CI | авто |
| Нагрузка: **1000** постов/мин × **3** приёмника — дублей **0**, петель **0**, p95 без delay **&lt; 60 с** | TC-XP-17 | harness `tests/load/scenarios/l5-p2-crosspost-ingest.js` (приём хука, не сверка дублей/p95). `l6-crosspost-fanout.js` и `Qa14CrosspostLoadIT` нет. Прогон full не снимался | nightly при `CHANNEL_ID`+`VK_SECRET` | **нет замера** |

## Уровни LoopGuard (L1–L7)

| Слой | Unit | IT |
|---|---|---|
| L1 own registry | `LoopGuardAndGraphTest` (`l1SkipsOwnPublication`) | `Qa14CrosspostLoopsIT#loopAbNoReverse` |
| L2 intent + 15s | `LoopGuardAndGraphTest#l2BlocksIntentFingerprint` | `Qa14CrosspostLoopsIT#registryLagBlockedByIntent` (intent есть, строки реестра нет; отложка 15 с в этом IT выключена, `defer-seconds=PT0S`) |
| L3 lineage | `LoopGuardAndGraphTest#l3BlocksHopCopiesAsSource` | IT нет: процессор не переносит hop/root из реестра, треугольный тест закрывается L1 |
| L4 target dedup | `LoopGuardAndGraphTest#l4ExactFingerprintDuplicate` | `Qa14CrosspostLoopsIT#fanInOneCopyOnSharedTarget` |
| L5 graph warnings | `LoopGuardAndGraphTest#l5DetectsCycleAb` | IT `Qa14CrosspostGraphWarnIT` нет |
| L6 auto_pause | `CircuitBreakerTest` | IT нет |
| L7 bot identity | `LoopGuardAndGraphTest#l7MaxBotIdentity` | IT пары Max нет |

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