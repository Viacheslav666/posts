package ru.posts.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TC-RBAC-06 (QA-6): полная матрица роль × action из roles-permissions.yaml.
 *
 * <p>Генерируется из YAML BE-6; не дублирует HTTP-сценарии {@code WorkspaceRbacIntegrationTest}.
 */
class RbacMatrixFullCoverageTest {

    private static final RbacMatrix MATRIX = RbacMatrixLoader.loadDefault();
    private static final RbacPermissionEvaluator EVALUATOR = new RbacPermissionEvaluator(MATRIX);
    private static final UUID PROJECT = UUID.fromString("019a0000-0003-7000-8000-0000000000aa");
    private static final UUID USER = UUID.fromString("019a0000-0003-7000-8000-000000000001");

    static Stream<Arguments> allRoleActionPairs() {
        List<Arguments> args = new ArrayList<>();
        for (RbacMatrixEntry entry : MATRIX.entries()) {
            for (RbacRole role : RbacRole.values()) {
                boolean expected = entry.effectFor(role) == RbacEffect.allow;
                args.add(Arguments.of(role.name(), entry.action(), expected));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "TC-RBAC-06 {0} × {1} → {2}")
    @MethodSource("allRoleActionPairs")
    @DisplayName("TC-RBAC-06 full YAML matrix")
    void everyRoleActionMatchesYaml(String role, String action, boolean expectedAllow) {
        RbacRole rbacRole = RbacRole.from(role);
        RbacMatrixEntry entry = MATRIX.require(action);
        // Context satisfies YAML conditions so effect=allow is observable via evaluator.
        assertThat(EVALUATOR.isAllowed(rbacRole, action, contextSatisfying(entry, rbacRole)))
                .as("%s %s", role, action)
                .isEqualTo(expectedAllow);
    }

    private static RbacEvaluationContext contextSatisfying(RbacMatrixEntry entry, RbacRole role) {
        List<String> conditions = entry.conditionsFor(role);
        boolean clientApproval = conditions.contains("client_approval_enabled");
        if (conditions.contains("own_posts_only")) {
            return new RbacEvaluationContext(PROJECT, Set.of(PROJECT), USER, USER, clientApproval);
        }
        return new RbacEvaluationContext(PROJECT, Set.of(PROJECT), null, USER, clientApproval);
    }

    @ParameterizedTest(name = "matrix has effects for all roles: {0}")
    @MethodSource("actions")
    void everyActionDeclaresAllRoles(String action) {
        RbacMatrixEntry entry = MATRIX.require(action);
        for (RbacRole role : RbacRole.values()) {
            assertThat(entry.effects()).containsKey(role);
        }
    }

    static Stream<String> actions() {
        return MATRIX.entries().stream().map(RbacMatrixEntry::action);
    }
}
