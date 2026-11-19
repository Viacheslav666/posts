package ru.posts.api.config;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.automation.suggest.SuggestService;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.suggest.SuggestDtos;

@Configuration
@Profile("openapi")
public class OpenApiSuggestStubConfig {

    @Bean
    SuggestService suggestService() {
        return new SuggestService() {
            @Override
            public CursorPage.SuggestSettingsPage listSettings(
                    UUID userId, UUID workspaceId, UUID projectId, CursorPage.Params page) {
                return new CursorPage.SuggestSettingsPage(List.of(), null, false);
            }

            @Override
            public SuggestDtos.SuggestSettings createSettings(
                    UUID userId,
                    UUID workspaceId,
                    UUID projectId,
                    SuggestDtos.SuggestSettingsCreate request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettings getSettings(UUID userId, UUID settingsId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettings patchSettings(
                    UUID userId, UUID settingsId, SuggestDtos.SuggestSettingsPatch request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettings activate(UUID userId, UUID settingsId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettings pause(UUID userId, UUID settingsId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettings resume(UUID userId, UUID settingsId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSettingsCopyResult copy(
                    UUID userId,
                    UUID settingsId,
                    SuggestDtos.SuggestSettingsCopyRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestSyncResult syncLast100(
                    UUID userId, UUID settingsId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.SuggestItemPage listItems(
                    UUID userId,
                    UUID settingsId,
                    String status,
                    String authorVkId,
                    Instant from,
                    Instant to,
                    CursorPage.Params page) {
                return new CursorPage.SuggestItemPage(List.of(), null, false);
            }

            @Override
            public SuggestDtos.SuggestItem getItem(UUID userId, UUID itemId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestItem approve(
                    UUID userId,
                    UUID itemId,
                    SuggestDtos.SuggestItemApproveRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestItem reject(
                    UUID userId, UUID itemId, SuggestDtos.SuggestItemRejectRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestBulkJob bulk(
                    UUID userId,
                    UUID settingsId,
                    SuggestDtos.SuggestBulkRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public SuggestDtos.SuggestBulkJob getBulkJob(UUID userId, UUID jobId) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
