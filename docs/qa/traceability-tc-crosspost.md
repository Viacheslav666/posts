# Матрица трассировки TC-XP → автотест (QA-14 / BE-36)

Обновлено: 2026-11-19.  
Инварианты: петли L1–L7, идемпотентность inbound, UNKNOWN_OUTCOME → 0 дублей.

| Пункт приёмки BE-36 | TC | Класс / путь | Набор | Статус |
|---|---|---|---|---|
| VK (текст + **3 фото**) → по **1** публикации в TG, OK, Max с медиа и форматированием | TC-XP-01 | `Qa14CrosspostPairsIT#vkThreePhotosFanout` | CI | авто |
| TG album **3 фото** → **1** VK пост (3 attachments в variant) | TC-XP-02 | `Qa14CrosspostPairsIT#tgAlbumToVk` | CI | авто |
| **Max → TG** | TC-XP-03 | `Qa14CrosspostPairsIT#maxToTg` | CI | авто |
| TG текст **&gt;1024** + фото → медиа + текст, **порядок** верный | TC-XP-04 | `Qa14CrosspostPairsIT#tgLongTextOrderPreserved` | CI | авто |
| **Петли:** A↔B активны, 1 пост в A → **1** в B, **0** обратно в A | TC-XP-05 | `Qa14CrosspostLoopsIT#loopAbNoReverse` | CI | **частично** (без publisher) |
| Петля при **задержке коммита реестра** (гонка, **L2**) | TC-XP-06 | `Qa14CrosspostLoopsIT#registryLagBlockedByIntent` | CI | авто |
| **Треугольник** A→B→C→A → по 1 в B и C, **0** в A | TC-XP-07 | `Qa14CrosspostLoopsIT#triangleSingleCopies` | CI | авто |
| **A→C** и **B→C** (B копия из A) → в C **одна** копия (**L4**) | TC-XP-08 | `Qa14CrosspostLoopsIT#fanInOneCopyOnSharedTarget` | CI | авто |
| Повтор события **×5** → **одна** публикация на приёмник | TC-XP-09 | `Qa14CrosspostLoopsIT#duplicateInboundFiveTimes` | CI | авто |
| `UNKNOWN_OUTCOME` публикатора → **0 дублей** | TC-XP-10 | `Qa14CrosspostReconcileIT#crosspostUnknownOutcomeReconcilesToSinglePost` | CI | авто |
| **Автовыключатель** (L6) срабатывает | TC-XP-11 | `LoopGuardAndGraphTest`; unit `CircuitBreakerTest` | CI unit | **частично** |
| Рекламный пост (**erid**) пропущен, `ad_post_excluded` | TC-XP-12 | `Qa14CrosspostPairsIT#eridPostSkipped`; `Qa14AntispamIT#crosspostEridExcluded` | CI | авто |
| TG файл **&gt;20 МБ** без Local Bot API → `skipped_unsupported_media` | TC-XP-13 | `Qa14CrosspostLoopsIT#telegramFileOver20MbSkipped` | CI | авто |
| Пост через Posts в A **не** переносится (default); опубликованная **suggest** — по опции | TC-XP-14 | `LoopGuardAndGraphTest` (unit) | CI unit | **частично** |
| Inbound restart **без потерь и дублей** | TC-IN-restart | `Qa14IngestRestartIT#pauseMidStreamThenResumeWithoutDupes` | nightly | авто |
| Нагрузка: **1000** постов/мин × **3** приёмника — дублей **0**, петель **0**, p95 **&lt; 60 с** (хук) | TC-XP-17 | `tests/load/scenarios/l5-p2-crosspost-ingest.js` + IT сверху | nightly | **частично** |

## Запуск

```bash
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.qa14.Qa14Crosspost*'
cd backend && ./gradlew :apps:publisher:integrationTest --tests 'ru.posts.publisher.pipeline.Qa14CrosspostReconcileIT'
```