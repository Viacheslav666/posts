# Матрица трассировки TC-AD → автотест (QA-10 / QA-13 / BE-15 / FE-12)

Обновлено: 2026-11-18.  
Инварианты: **fail-closed без erid**; **идемпотентность creative external_id**; **0 дублей** рекламной публикации (сверка emulator `/_control/posts/count` + `/_control/verify`); **VK ORD live только sandbox** (`OrdSandboxGuard`).

| TC | Приоритет | Автотест | Набор | Статус |
|---|---|---|---|---|
| TC-AD-01 | P0 | `tests/e2e/specs/ad-marking.spec.ts` · FE `option-support.test.ts` / `post-options-panel.test.tsx` | CI e2e-ad + frontend | авто |
| TC-AD-02 | P0 | `OrdAdMarkingIT#recurrenceMaterializesPublications` · FE `recurrence.test.ts` · e2e | CI | авто |
| TC-AD-03 | P0 | `OrdAdMarkingIT#scheduleAdWithoutOrdBlocked` · FE `post-editor.test.tsx` · e2e | CI | авто |
| TC-AD-04 | P0 | `OrdAdMarkingIT#registerCreativeIdempotent`, `#scheduleAdRegistersErid`, `#yandexRegisterCreative` · `OrdAdPipelineIT#tcAdScheduledPublishWithMarker` · e2e · `Qa13ParityOrdE2EIT` | CI | авто |
| TC-AD-05 | P0 | `OrdAdMarkingIT#reportRunIdempotent` · `OrdReportingIT#fullCycleVkSubmittedAndIdempotent` · `Qa13ParityOrdE2EIT` | CI | авто |
| TC-AD-06 | P0 | `OrdAdPipelineIT#tcAd06AutoDeleteAfterPeriod` · `OrdReportingIT#multiMonthAndTakedownFinalStats` | CI | авто |
| TC-AD-05b (BE-26) | P0 | `OrdReportingIT#eridCheckAndDeleteInvoice` · `#yandexReportCycle` · `#tenThousandCreativesBudget` | CI | авто |
| TC-AD-placement (QA-13) | P0 | `Qa13AdPlacementPipelineIT` · `Qa13ParityOrdE2EIT` (text_end) · `OrdPrePublishHookTest` | CI | авто |
| TC-AD-sandbox | P1 | `OrdVkSandboxIT` / `OrdYandexSandboxIT` (`@Tag("ord-sandbox")`, `OrdSandboxGuard`, workflow `ord-sandbox.yml`) | nightly/manual | авто (skip без secret) |
| TC-AD-07 | P0 | `OrdAdPipelineIT#tcAd07RetryNoDuplicate` (регресс TC-PUB на ads) | CI | авто |
| TC-AD-08 | P0 | `OrdAdMarkingIT#repostWithAdMarking` | CI | авто |
| TC-AD-09 | P0 | `OrdAdMarkingIT#rbacAuthorAndViewerDenied` (+ RBAC matrix unit) | CI | авто |
| TC-AD-10 | P1 | `OrdAdMarkingIT#previewShowsMarker` | CI | авто |
| TC-AD-11 | P0 | `OrdAdMarkingIT#invalidInnRejected` · `AdMarkingValidatorTest` | CI | авто |
| TC-AD-12 | P0 | `OrdAdMarkingIT#ord5xxFailClosedOnSchedule`, `#ord429FailClosedOnSchedule`, `#ordTimeoutFailClosedOnSchedule` · `OrdAdPipelineIT#tcAd12FailClosedWithoutErid` · provider WM 5xx/429/timeout | CI | авто |
| TC-AD-13 | P0 | `OrdAdMarkingIT#registerCreativeIdempotent` · `VkOrdProviderWireMockTest#idempotentPutReturnsSameErid` · Yandex analogue | CI | авто |
| TC-AD-14 | P1 | `OrdAdMarkingIT#selfPromoSkipsContract` | CI | авто |
| TC-AD-15 | P1 | `OrdAdMarkingIT#yandexConnectionTest` | CI | авто |
| TC-AD-16 | P1 | `OrdAdMarkingIT#dictionariesAvailable` | CI | авто |
| TC-AD-17 | P1 | `OrdAdMarkingIT#postOptionsParity` | CI | авто |
| TC-AD-18 | P0 | `OrdAdMarkingIT#foreignCreativeIdor` | CI | авто |

## WireMock ORD

| Провайдер | Happy path / ошибки | Класс |
|---|---|---|
| VK ОРД | stub helper + 401/429/5xx/timeout | `OrdWireMockStubs`, `VkOrdProviderWireMockTest` |
| Яндекс ОРД | stub helper + 401/429/5xx/timeout | `OrdWireMockStubs`, `YandexOrdProviderWireMockTest` |

Фикстуры erid: префикс `test-erid-...` (gitleaks-safe).

## Запуск

```bash
# CI (API ORD IT + QA-13 parity E2E + publisher ad pipeline)
cd backend && ./gradlew :apps:api:integrationTest --tests 'ru.posts.api.ord.*' --tests 'ru.posts.api.qa13.*' \
  :apps:publisher:integrationTest --tests 'ru.posts.publisher.pipeline.OrdAdPipelineIT' \
  --tests 'ru.posts.publisher.pipeline.Qa13AdPlacementPipelineIT'

# E2E UI маркировки (MSW / demo)
cd tests/e2e && pnpm test:ad-marking

# Live VK ORD sandbox only (never api.ord.vk.com) — skip without VK_ORD_SANDBOX_API_KEY
cd backend && ./gradlew :apps:api:integrationTest -PincludeOrdSandbox=true \
  --tests 'ru.posts.api.ord.OrdVkSandboxIT'

# Nightly — полный publisher IT (+ load) и e2e ad-marking на compose full
# P0 coverage: python3 docs/qa/generate_p0_parity_coverage_report.py
```
