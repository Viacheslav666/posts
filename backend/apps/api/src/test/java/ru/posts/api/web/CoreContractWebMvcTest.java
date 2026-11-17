package ru.posts.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.posts.api.error.ApiExceptionHandler;

@WebMvcTest(controllers = {MeController.class, WorkspaceController.class, PingController.class})
@Import(ApiExceptionHandler.class)
@ActiveProfiles("local")
class CoreContractWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meReturnsLocalFixture() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("local@posts.test"))
                .andExpect(jsonPath("$.memberships[0].role").value("owner"));
    }

    @Test
    void workspacesReturnProblemDetail501() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("not_implemented"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("workspacesList")));
    }

    @Test
    void pingStillWorks() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
