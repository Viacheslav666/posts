package ru.posts.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.ChannelSetRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.rbac.RbacRole;

/**
 * QA-6 TC-RBAC: роль × эндпоинт из OpenAPI/карты. Skip при 501; allow → ≠401/403; deny → 403.
 *
 * <p>Сценарные TC-RBAC-01…05 остаются в {@code WorkspaceRbacIntegrationTest} (не дублируются).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RbacEndpointMatrixIT {

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
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelSetRepository channelSetRepository;

    private RbacHttpProbeSupport probes;
    private EndpointIds ids;

    @BeforeEach
    void setUp() throws Exception {
        if (probes == null) {
            probes = new RbacHttpProbeSupport(
                    mockMvc,
                    objectMapper,
                    mailSender,
                    planRepository,
                    workspaceRepository,
                    channelRepository,
                    channelSetRepository);
        }
        if (ids == null) {
            ids = probes.seedTeamFixture();
        }
    }

    static Stream<Arguments> roleEndpointCases() {
        List<Arguments> args = new ArrayList<>();
        for (RbacEndpointMapLoader.Endpoint endpoint : RbacEndpointMapLoader.loadMatrixEndpoints()) {
            for (RbacRole role : RbacRole.values()) {
                boolean allowed = RbacHttpProbeSupport.isAllowed(role.name(), endpoint.action());
                args.add(Arguments.of(role.name(), endpoint.operationId(), endpoint.action(), allowed, endpoint));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "TC-RBAC {0} × {1} ({2}) allow={3}")
    @MethodSource("roleEndpointCases")
    @DisplayName("TC-RBAC matrix: role × endpoint")
    void roleTimesEndpoint(
            String role, String operationId, String action, boolean allowed, RbacEndpointMapLoader.Endpoint endpoint)
            throws Exception {
        assumeFalse(
                probes.isNotImplemented(ids, endpoint),
                () -> "skip " + operationId + ": backend returns 501 (not implemented yet)");

        int status = probes.perform(ids.tokenFor(role), endpoint, ids, Scope.OWN);
        if (allowed) {
            assertThat(status)
                    .as("%s %s (%s) expected allow", role, operationId, action)
                    .isNotIn(401, 403);
            assertThat(status).as("still stub?").isNotEqualTo(501);
        } else {
            assertThat(status)
                    .as("%s %s (%s) expected deny → 403", role, operationId, action)
                    .isEqualTo(403);
        }
    }
}
