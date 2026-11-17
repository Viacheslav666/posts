package ru.posts.api.config;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.media.MediaService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.media.MediaDtos;

/** Stubs so openapi profile can boot MediaController without S3/DB. */
@Configuration
@Profile("openapi")
public class OpenApiMediaStubConfig {

    @Bean
    MediaService mediaService() {
        return new MediaService() {
            @Override
            public CursorPage.MediaPage list(
                    UUID userId,
                    UUID workspaceId,
                    ApiEnums.MediaKind kind,
                    ApiEnums.MediaStatus status,
                    String q,
                    String folder,
                    String tag,
                    CursorPage.Params page) {
                return new CursorPage.MediaPage(java.util.List.of(), null, false);
            }

            @Override
            public MediaDtos.MediaAsset get(UUID userId, UUID mediaId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public MediaDtos.CreateUploadResponse createUpload(
                    UUID userId,
                    MediaDtos.CreateUploadRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public MediaDtos.MediaAsset complete(
                    UUID userId, UUID mediaId, MediaDtos.CompleteUploadRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID mediaId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public MediaDtos.ImportUrlResponse importUrl(
                    UUID userId, MediaDtos.ImportUrlRequest request, String idempotencyKey, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
