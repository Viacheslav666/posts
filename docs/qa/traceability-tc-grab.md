# Матрица трассировки TC-GRAB → автотест (QA-12 / QA-13 / E17)

Обновлено: 2026-11-18.  
Инварианты: **default `publish_mode=moderate`** (нет публикаций до approve); **0 дублей** remote/content_hash/**similarity**; SSRF fail-closed; публикация только через конвейер BE-10.

| TC | Приоритет | Автотест | Набор | Статус |
|---|---|---|---|---|
| TC-GRAB-01 | P0 | `GrabberIngestIntegrationTest#createRuleDefaultsModerate` | CI | авто |
| TC-GRAB-02 | P0 | `GrabberQa12IntegrationTest#scheduledRunAndRedelivery` | CI | авто |
| TC-GRAB-03 | P0 | `GrabberIngestIntegrationTest#moderateApproveRejectDedup`; `GrabberQa12IntegrationTest#manualRateLimitAndRemoteDedup` (429) | CI | авто |
| TC-GRAB-04 | P0 | `GrabberQa12IntegrationTest#attributionAndHistory` | CI | авто |
| TC-GRAB-05 | P0 | `GrabberIngestIntegrationTest#moderateApproveRejectDedup`; `GrabberQa12IntegrationTest#manualRateLimitAndRemoteDedup` | CI | авто |
| TC-GRAB-06 | P0 | `GrabberIngestIntegrationTest#contentHashDedup` | CI | авто |
| TC-GRAB-07 | P0 | `GrabberIngestIntegrationTest#activateRequiresAttributionAndResponsibility` | CI | авто |
| TC-GRAB-08 | P0 | `GrabberIngestIntegrationTest#moderateApproveRejectDedup`; e2e `grabber-moderation.spec.ts` (MSW) | CI | авто |
| TC-GRAB-08b | P0 | `GrabberIngestIntegrationTest#createRuleDefaultsModerate`; FE `rule-form.test.tsx` | CI | авто |
| TC-GRAB-09 | P0 | `GrabberIngestIntegrationTest#moderateApproveRejectDedup`; `GrabberQa12IntegrationTest#attributionAndHistory` | CI | авто |
| TC-GRAB-10 | P0 | `GrabberIngestIntegrationTest#pauseResume` | CI | авто |
| TC-GRAB-11 | P0 | `GrabberIngestIntegrationTest#rssAndSsrf`; `GrabberQa12IntegrationTest#autoPublishMode` | CI | авто |
| TC-GRAB-12 | P0 | `SsrfGuardTest`; `SafeHttpFetcherSsrfTest`; `GrabberIngestIntegrationTest#rssAndSsrf` / `#websiteExtractAndSsrf`; `GrabberQa12IntegrationTest#websiteXssAndExtraSsrf` | CI | авто |
| TC-GRAB-13 | P0 | `GrabberQa12IntegrationTest#scheduledRunAndRedelivery` | CI | авто |
| TC-GRAB-14 | P0 | `GrabberQa12IntegrationTest#pipelineLinkageNoDuplicate` | nightly (`@Tag("nightly")`) | авто |
| TC-GRAB-15 | P0 | `GrabberQa12IntegrationTest#rbacAuthorViewer`; `RbacEndpointMatrixIT` (ingest ops) | CI | авто |
| TC-GRAB-16 | P0 | `GrabberIngestIntegrationTest#idorForeignRule`; `RbacIdorMatrixIT` | CI | авто |
| TC-GRAB-17 | P1 | `GrabberQa12IntegrationTest#auditTrail` | CI | авто |
| TC-GRAB-18 | P1 | `GrabberQa12IntegrationTest#autoPauseOnErrors` | CI | авто |
| TC-GRAB-19 | P1 | `GrabberFiltersTest`; `GrabberQa12IntegrationTest#filterAds`; `GrabberQa13IntegrationTest#stopWordsFilter` | CI | авто |
| TC-GRAB-similarity (BE-27 / QA-13) | P0 | `SimHasherTest`; `GrabberQa13IntegrationTest#similarityNearDuplicateSuppressed` / `#distantTextsNotSuppressedBySimilarity` | CI | авто |
| TC-GRAB-20 | P1 | `GrabberQa12IntegrationTest#autoRateLimitQueues` | CI | авто |
| TC-GRAB-21 | P1 | `GrabberIngestIntegrationTest#websiteExtractAndSsrf`; `WebsiteHtmlExtractorTest`; `GrabberQa12IntegrationTest#websiteXssAndExtraSsrf` | CI | авто |
| TC-GRAB-22 | P0 | `GrabberIngestIntegrationTest#createRuleDefaultsModerate`; `SourceIdNormalizerTest` | CI | авто |
| TC-GRAB-23 | P0 | `GrabberIngestIntegrationTest#moderateApproveRejectDedup` | CI | авто |
| TC-GRAB-24 | P1 | `GrabberQa12IntegrationTest#bulkAndEditApprove` | CI | авто |
| TC-GRAB-25 | P0 | `GrabberQa12IntegrationTest#validationLimit` | CI | авто |
| TC-GRAB-26 | P1 | `GrabberQa12IntegrationTest#channelReauthAutoPause` | CI | авто |
| TC-GRAB-27 | P1 | `GrabberQa12IntegrationTest#bulkAndEditApprove` | CI | авто |
| TC-GRAB-28 | P2 | - | - | ручной / кандидат |
| FE P0 UX | P0 | `tests/e2e/specs/grabber-moderation.spec.ts` (MSW) | CI `e2e-smoke` | авто |
| Full-stack E2E | P1 | `tests/e2e/specs/grabber-fullstack.spec.ts` | nightly (`E2E_GRABBER_FULLSTACK=1`) | авто |
| Emulator wall fixtures | - | `WallSourceApiTest`; `PUT /_control/vk/wall/{ownerId}` | CI `social-emulator` | авто |

## Фикстуры

| Путь | Назначение |
|---|---|
| `tests/fixtures/grabber/rss-sample.xml` | RSS 2.0 + XSS в description |
| `tests/fixtures/grabber/article-xss.html` | HTML/OG + XSS для website |
| `tests/fixtures/grabber/vk-wall-response.json` | Пример `wall.get` |
| Emulator `WallSourceStore` | Seed VK wall для grabber ingest |

## Запуск

```bash
# CI - unit + IT (без @Tag("nightly"))
cd backend && ./gradlew :libs:grabber:test :apps:api:integrationTest

# Nightly - + TC-GRAB-14 pipeline tag + fullstack e2e
cd backend && ./gradlew :apps:api:integrationTest -PincludeNightly=true
E2E_GRABBER_FULLSTACK=1 pnpm --dir tests/e2e test:grabber-fullstack

# Emulator
cd tests/social-emulator && ./gradlew test
```

## SSRF набор (TC-GRAB-12)

Покрыто unit/IT: loopback IPv4/IPv6, link-local/`fe80`, ULA/`fc00`, site-local, CGNAT `100.64/10`, metadata `169.254.169.254`, `metadata.google.internal`, redirect → metadata, DNS rebinding, max redirects, max bytes, схемы кроме http(s) (`ftp`, `file`, `gopher`), XSS strip в HTML (`HtmlSanitizer` / website extract).
