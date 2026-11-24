package ru.posts.api.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.api.error.ApiExceptionHandler;
import ru.posts.api.media.MediaService;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@WebMvcTest(controllers = MediaController.class)
@Import(ApiExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class MediaControllerRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaService mediaService;

    private final UUID userId = UUID.fromString("019a0000-0001-7000-8000-000000000001");
    private final UUID workspaceId = UUID.fromString("019a0000-0002-7000-8000-000000000002");

    @Test
    void foldersAndQuotaAreNotParsedAsMediaId() throws Exception {
        when(mediaService.listFolders(eq(userId), eq(workspaceId)))
                .thenReturn(new MediaDtos.MediaFolderList(
                        List.of(new MediaDtos.MediaFolder("Кампании", "Кампании", workspaceId))));
        when(mediaService.quota(eq(userId), eq(workspaceId)))
                .thenReturn(new MediaDtos.MediaQuota(workspaceId, 10, 1000, 1, 0));

        try (MockedStatic<SecurityAuthSupport> auth = Mockito.mockStatic(SecurityAuthSupport.class)) {
            auth.when(SecurityAuthSupport::requirePrincipal).thenReturn(new PostsPrincipal(userId, null));
            mockMvc.perform(get("/api/v1/media/folders").param("workspaceId", workspaceId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].name").value("Кампании"));
            mockMvc.perform(get("/api/v1/media/quota").param("workspaceId", workspaceId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usedBytes").value(10))
                    .andExpect(jsonPath("$.limitBytes").value(1000));
            mockMvc.perform(get("/api/v1/media/not-a-uuid")).andExpect(status().isNotFound());
        }
    }

    @Test
    void uuidMediaIdStillResolves() throws Exception {
        UUID mediaId = UUID.fromString("019a0000-0004-7000-8000-000000000004");
        when(mediaService.get(eq(userId), eq(mediaId))).thenReturn(sampleAsset(mediaId));
        try (MockedStatic<SecurityAuthSupport> auth = Mockito.mockStatic(SecurityAuthSupport.class)) {
            auth.when(SecurityAuthSupport::requirePrincipal).thenReturn(new PostsPrincipal(userId, null));
            mockMvc.perform(get("/api/v1/media/" + mediaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(mediaId.toString()));
        }
    }

    private static MediaDtos.MediaAsset sampleAsset(UUID mediaId) {
        return new MediaDtos.MediaAsset(
                mediaId,
                UUID.fromString("019a0000-0002-7000-8000-000000000002"),
                UUID.fromString("019a0000-0001-7000-8000-000000000001"),
                ru.posts.api.web.dto.common.ApiEnums.MediaKind.image,
                "image/jpeg",
                10,
                null,
                null,
                null,
                null,
                ru.posts.api.web.dto.common.ApiEnums.MediaStatus.ready,
                java.util.Map.of(),
                java.time.Instant.parse("2026-11-01T00:00:00Z"),
                "a.jpg",
                null,
                List.of(),
                null,
                List.of(),
                null);
    }
}
