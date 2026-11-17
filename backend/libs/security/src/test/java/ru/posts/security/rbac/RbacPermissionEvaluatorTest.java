package ru.posts.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbacPermissionEvaluatorTest {

    private static RbacPermissionEvaluator evaluator;

    @BeforeAll
    static void loadMatrix() {
        evaluator = new RbacPermissionEvaluator(RbacMatrixLoader.loadDefault());
    }

    @ParameterizedTest(name = "TC-RBAC-06 {0} {1} → {2}")
    @CsvSource({
        "owner, workspace.view, true",
        "admin, workspace.view, true",
        "editor, workspace.view, true",
        "author, workspace.view, true",
        "viewer, workspace.view, true",
        "owner, members.invite, true",
        "admin, members.invite, true",
        "editor, members.invite, false",
        "author, members.invite, false",
        "viewer, members.invite, false",
        "owner, channels.manage, true",
        "admin, channels.manage, true",
        "editor, channels.manage, false",
        "owner, posts.schedule, true",
        "admin, posts.schedule, true",
        "editor, posts.schedule, true",
        "author, posts.schedule, false",
        "viewer, posts.schedule, false",
        "owner, posts.create, true",
        "admin, posts.create, true",
        "editor, posts.create, true",
        "author, posts.create, true",
        "viewer, posts.create, false",
        "owner, audit.view, true",
        "admin, audit.view, true",
        "editor, audit.view, false",
        "author, audit.view, false",
        "viewer, audit.view, false",
        "owner, billing.manage, true",
        "admin, billing.manage, false",
        "editor, billing.manage, false",
        "owner, workspace.delete, true",
        "admin, workspace.delete, false",
    })
    void tcRbac06_smokeMatrix(String role, String action, boolean expected) {
        assertThat(evaluator.isAllowed(RbacRole.from(role), action)).isEqualTo(expected);
    }

    @Test
    @DisplayName("TC-RBAC-02 Author cannot schedule")
    void tcRbac02_authorCannotSchedule() {
        assertThat(evaluator.isAllowed(RbacRole.author, "posts.create")).isTrue();
        assertThat(evaluator.isAllowed(RbacRole.author, "posts.schedule")).isFalse();
        assertThat(evaluator.isAllowed(RbacRole.author, "posts.bulk")).isFalse();
    }

    @Test
    @DisplayName("assigned_projects denies project outside grants for editor")
    void assignedProjectsScope() {
        UUID alpha = UUID.fromString("019a0000-0003-7000-8000-0000000000aa");
        UUID beta = UUID.fromString("019a0000-0003-7000-8000-0000000000bb");
        RbacEvaluationContext inGrant = RbacEvaluationContext.forProject(alpha, Set.of(alpha));
        RbacEvaluationContext outGrant = RbacEvaluationContext.forProject(beta, Set.of(alpha));

        assertThat(evaluator.isAllowed(RbacRole.editor, "projects.view", inGrant))
                .isTrue();
        assertThat(evaluator.isAllowed(RbacRole.editor, "projects.view", outGrant))
                .isFalse();
        assertThat(evaluator.isAllowed(RbacRole.admin, "projects.view", outGrant))
                .isTrue();
    }

    @Test
    @DisplayName("own_posts_only blocks author editing foreign post")
    void ownPostsOnly() {
        UUID author = UUID.fromString("019a0000-0003-7000-8000-000000000001");
        UUID other = UUID.fromString("019a0000-0003-7000-8000-000000000002");
        UUID project = UUID.fromString("019a0000-0003-7000-8000-0000000000aa");
        RbacEvaluationContext own = new RbacEvaluationContext(project, Set.of(project), author, author, false);
        RbacEvaluationContext foreign = new RbacEvaluationContext(project, Set.of(project), other, author, false);

        assertThat(evaluator.isAllowed(RbacRole.author, "posts.update", own)).isTrue();
        assertThat(evaluator.isAllowed(RbacRole.author, "posts.update", foreign))
                .isFalse();
        assertThat(evaluator.isAllowed(RbacRole.editor, "posts.update", foreign))
                .isTrue();
    }
}
