package ru.posts.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import ru.posts.api.auth.AuthService;
import ru.posts.api.error.ApiExceptionHandler;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.workspace.WorkspaceService;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@WebMvcTest(controllers = {MeController.class, WorkspaceController.class, PingController.class})
@Import(ApiExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CoreContractWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private WorkspaceService workspaceService;

    @Test
    void meReturnsProfile() throws Exception {
        UUID userId = UUID.fromString("019a0000-0002-7000-8000-000000000001");
        PostsPrincipal principal = new PostsPrincipal(userId, null);
        when(authService.me(principal))
                .thenReturn(new UserDtos.UserProfile(
                        userId,
                        "local@posts.test",
                        true,
                        "Local Dev",
                        null,
                        "Europe/Moscow",
                        "ru",
                        false,
                        Instant.parse("2026-11-01T10:00:00Z"),
                        List.of(new UserDtos.MembershipSummary(
                                UUID.fromString("019a0000-0002-7000-8000-000000000002"),
                                "Личное пространство",
                                ApiEnums.MemberRole.owner,
                                ApiEnums.BillingStatus.trial)),
                        false,
                        java.util.Map.of(),
                        null,
                        List.of()));

        try (MockedStatic<SecurityAuthSupport> mocked = Mockito.mockStatic(SecurityAuthSupport.class)) {
            mocked.when(SecurityAuthSupport::requirePrincipal).thenReturn(principal);
            mockMvc.perform(get("/api/v1/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("local@posts.test"))
                    .andExpect(jsonPath("$.memberships[0].role").value("owner"));
        }
    }

    @Test
    void workspacesListReturnsPage() throws Exception {
        UUID userId = UUID.fromString("019a0000-0002-7000-8000-000000000001");
        UUID workspaceId = UUID.fromString("019a0000-0002-7000-8000-000000000002");
        when(workspaceService.list(eq(userId), any()))
                .thenReturn(new CursorPage.WorkspacePage(
                        List.of(new WorkspaceDtos.Workspace(
                                workspaceId,
                                "Личное пространство",
                                userId,
                                "free",
                                ApiEnums.BillingStatus.trial,
                                Instant.parse("2026-11-24T10:00:00Z"),
                                null,
                                Instant.parse("2026-11-17T10:00:00Z"))),
                        null,
                        false));

        try (MockedStatic<SecurityAuthSupport> mocked = Mockito.mockStatic(SecurityAuthSupport.class)) {
            mocked.when(SecurityAuthSupport::requirePrincipal).thenReturn(new PostsPrincipal(userId, null));
            mockMvc.perform(get("/api/v1/workspaces"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].name").value("Личное пространство"))
                    .andExpect(jsonPath("$.hasMore").value(false));
        }
    }

    @Test
    void pingStillWorks() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
