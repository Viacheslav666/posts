# Матрица трассировки TC-STORY → автотест (QA-11 / E11)

Обновлено: 2026-11-18.  
Инварианты: **0 дублей** remote story (emulator `/_control/posts?contentType=story` + `/_control/verify`); **fail-closed без erid** для ads; TG/Max/IG out of scope; VK story-connect обязателен.

| TC | Приоритет | Автотест | Набор | Статус |
|---|---|---|---|---|
| TC-STORY-01 | P0 | `StoriesApiIntegrationTest#scheduleVkStory`; `StoryVkPipelineIT#tcStory01VkPhotoPublish`; e2e `scheduled-stories.spec.ts`; `VkStoryAdapterWireMockTest` | CI | авто |
| TC-STORY-02 | P0 | `StoryOkPipelineIT#tcStory02OkMomentPublish`; `OkStoryAdapterWireMockTest`; emulator `OkMomentsFaultTest` | CI | авто |
| TC-STORY-03 | P0 | `StoriesApiIntegrationTest#scheduleVkStory` / `#okStoryNoSeparateConnect`; `StoryOkPipelineIT#tcStory03OkEffectiveTtl`; `StorySupportTest#okEffectiveTtl`; e2e | CI | авто |
| TC-STORY-04 | P0 | `StoriesQa11IntegrationTest#tcStory04VkInternalLinkAccepted`; `VkStoryAdapterWireMockTest#publishStoryPhotoWithLink` | CI | авто |
| TC-STORY-05 | P0 | `StoriesApiIntegrationTest#vkExternalLinkRejected`; `StorySupportTest#vkLinkValidation` | CI | авто |
| TC-STORY-06 | P0 | `StoriesApiIntegrationTest#vkStoryConnectRequired`; e2e `scheduled-stories.spec.ts` | CI | авто |
| TC-STORY-07 | P0 | `StoriesApiIntegrationTest#okStoryNoSeparateConnect` | CI | авто |
| TC-STORY-08 | P0 | `StoriesQa11IntegrationTest#tcStory08EverydayPauseResume` | CI | авто |
| TC-STORY-09 | P0 | `StoryVkPipelineIT#tcStory09DeleteStoryAction` | CI | авто |
| TC-STORY-10 | P1 | `StoriesQa11IntegrationTest#tcStory10BatchThreeStories` | CI | авто |
| TC-STORY-11 | P0 | `StoriesQa11IntegrationTest#tcStory11VkStoryAdRegistersErid`; e2e ad form on stories | CI | авто |
| TC-STORY-12 | P0 | `StoriesQa11IntegrationTest#tcStory12OkAdsAllowedMaxRejected` | CI | авто |
| TC-STORY-13 | P0 | `StoriesApiIntegrationTest#telegramStoryRejected`; `StorySupportTest#unsupportedNetworks`; e2e IG absent / TG+Max disabled | CI | авто |
| TC-STORY-14 | P0 | `StoriesQa11IntegrationTest#tcStory14AuthorCannotSchedule` | CI | авто |
| TC-STORY-15 | P0 | `StoryVkPipelineIT#tcStory15NoDuplicateOnRedelivery` / `#tcStoryRetryNoDuplicate`; `StoryOkPipelineIT#tcStory15OkNoDuplicate`; `VkStoryAdapterWireMockTest#findExistingStory` | CI | авто |
| TC-STORY-16 | P1 | `StoriesApiIntegrationTest#calendarStoryFilter`; e2e calendar filter | CI | авто |
| TC-STORY-17 | P0 | `StoriesQa11IntegrationTest#tcStory17InvalidBmpMedia`; `StorySupportTest#mediaLimits` | CI | авто |
| TC-STORY-18 | P1 | — | — | кандидат |
| TC-STORY-19 | P0 | `StoriesQa11IntegrationTest#tcStory19IdorOtherWorkspace` | CI | авто |
| TC-STORY-20 | P0 | `StoryVkPipelineIT#tcStory20WaitingForChannel`; emulator `VkStoriesFaultTest#revokedStoryTokenReturnsAuthError` | CI | авто |
| TC-STORY-21 | P1 | — | — | кандидат |
| TC-STORY-22 | P1 | — | — | кандидат |
| TC-STORY-23 | P0 | `StoriesQa11IntegrationTest#tcStory23WeekdaysMaterialize` | CI | авто |
| TC-STORY-24 | P0 | `StoriesQa11IntegrationTest#tcStory24Ord5xxFailClosed`; `StoryVkPipelineIT#tcStory24AdFailClosedWithoutErid` | CI | авто |
| TC-STORY-25 | P2 | `StoriesQa11IntegrationTest#tcStory25BatchOverLimit` | CI | авто |
| TC-STORY-26 | P1 | — | — | кандидат |
| TC-STORY-27 | P0 | `NetworkCapabilitiesStoriesIT#storiesCapabilityFlags` | CI | авто |
| TC-STORY-28 | P1 | `StoriesQa11IntegrationTest#tcStory28MaxUnsupported` | CI | авто |
| TC-STORY-29 | P1 | — | ручной / doc | checklist |
| TC-STORY-30 | P0 | `StoriesQa11IntegrationTest#tcStory30OversizedVideoRejected` (`story_media_size`, ≤50 MB); `StorySupportTest#mediaLimits` | CI | авто |
| FE P0 UX | P0 | `tests/e2e/specs/scheduled-stories.spec.ts` (MSW) | CI `e2e-smoke` | авто |
| Emulator stories/moments | — | `VkApiTest`; `VkStoriesFaultTest`; `OkMaxApiTest`; `OkMomentsFaultTest` | CI `social-emulator` | авто |

## Эмулятор (QA-11)

| Путь | Назначение |
|---|---|
| VK `stories.getPhotoUploadServer` / `getVideoUploadServer` → upload → `stories.save` / `delete` | publish + idempotency (`guid`) |
| OK `moments.getUploadUrl` → upload → `moments.commit` / `delete` | Moments chain |
| `GET /_control/posts?contentType=story` | фильтр историй |
| `POST /_control/vk/story-tokens/{groupId}/revoke` | TC-STORY-20 |
| scenario `forcedError`: `5` / `429` / `5xx` / `hang` | ошибки / таймауты |
| `posts.emulator.public-base-url` | ephemeral port для upload_url в IT |

## Запуск

```bash
# Emulator unit
cd tests/social-emulator && ./gradlew test spotlessCheck

# API stories IT
cd backend && ./gradlew :apps:api:integrationTest \
  --tests 'ru.posts.api.post.Stories*' \
  --tests 'ru.posts.api.channel.NetworkCapabilitiesStoriesIT'

# Publisher VK/OK story pipeline
cd backend && ./gradlew :apps:publisher:integrationTest --tests 'ru.posts.publisher.pipeline.Story*'

# E2E UI (MSW)
cd tests/e2e && pnpm test:stories
```
