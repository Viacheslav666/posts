package ru.posts.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Условия scope/conditions (не дублирует полную матрицу — см. {@link RbacMatrixFullCoverageTest}).
 */
class RbacPermissionEvaluatorTest {

    private static RbacPermissionEvaluator evaluator;

    @BeforeAll
    static void loadMatrix() {
        evaluator = new RbacPermissionEvaluator(RbacMatrixLoader.loadDefault());
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
