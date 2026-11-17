package ru.posts.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.api.rbac.RbacHttpProbeSupport.EndpointIds;
import ru.posts.api.rbac.RbacHttpProbeSupport.Scope;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

/**
 * QA-6 TC-IDOR: доступ к ресурсу чужого workspace по ID → 404.
 *
 * <p>Базовый TC-IDOR-01 (workspace CRUD/audit) остаётся в {@code WorkspaceRbacIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RbacIdorMatrixIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingAuthMailSender mailSender;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    private RbacHttpProbeSupport probes;
    private EndpointIds ids;

    @BeforeEach
    void setUp() throws Exception {
        if (probes == null) {
            probes = new RbacHttpProbeSupport(
                    mockMvc, objectMapper, mailSender, planRepository, workspaceRepository, mediaAssetRepository);
        }
        if (ids == null) {
            ids = probes.seedTeamFixture();
        }
    }

    static Stream<Arguments> idorEndpoints() {
        return RbacEndpointMapLoader.loadMatrixEndpoints().stream()
                .filter(RbacIdorMatrixIT::isIdorCandidate)
                .map(Arguments::of);
    }

    private static boolean isIdorCandidate(RbacEndpointMapLoader.Endpoint endpoint) {
        if (endpoint.path().contains("{workspaceId}") || endpoint.query().containsKey("workspaceId")) {
            return true;
        }
        // resource-by-id that must not leak across workspaces
        return endpoint.path().contains("{projectId}")
                || endpoint.path().contains("{channelId}")
                || endpoint.path().contains("{postId}")
                || endpoint.path().contains("{mediaId}")
                || endpoint.path().contains("{publicationId}")
                || endpoint.path().contains("{setId}")
                || endpoint.path().contains("{invitationId}");
    }

    @ParameterizedTest(name = "TC-IDOR {0}")
    @MethodSource("idorEndpoints")
    @DisplayName("TC-IDOR matrix: foreign workspace/resource → 404")
    void foreignResourceIsNotFound(RbacEndpointMapLoader.Endpoint endpoint) throws Exception {
        assumeFalse(
                probes.isNotImplemented(ids, endpoint),
                () -> "skip " + endpoint.operationId() + ": backend returns 501 (not implemented yet)");

        int status = probes.perform(ids.ownerToken(), endpoint, ids, Scope.FOREIGN);
        assertThat(status)
                .as("TC-IDOR %s foreign → 404 (not 403)", endpoint.operationId())
                .isEqualTo(404);
    }
}
