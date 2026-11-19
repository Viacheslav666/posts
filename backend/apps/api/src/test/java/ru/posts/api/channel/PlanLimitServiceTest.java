package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.InvitationRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.UsageCounterRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    PlanRepository planRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    PostRepository postRepository;

    @Mock
    UsageCounterRepository usageCounterRepository;

    @Mock
    WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    InvitationRepository invitationRepository;

    @Mock
    AutomationRuleRepository automationRuleRepository;

    @Mock
    ru.posts.persistence.repository.CrosspostRuleRepository crosspostRuleRepository;

    PlanLimitService service;
    UUID workspaceId;
    UUID planId;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(
                workspaceRepository,
                planRepository,
                channelRepository,
                postRepository,
                usageCounterRepository,
                workspaceMemberRepository,
                invitationRepository,
                automationRuleRepository,
                crosspostRuleRepository);
        workspaceId = UuidV7.generate();
        planId = UuidV7.generate();
    }

    @Test
    void frozenWorkspaceBlocked() {
        WorkspaceEntity workspace = workspace(planId, "frozen");
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> service.ensureNotFrozen(workspaceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, ProblemCodes.BILLING_FROZEN));
    }

    @Test
    void grabberRuleLimitEnforced() {
        WorkspaceEntity workspace = workspace(planId, "active");
        PlanEntity plan = plan(Map.of("grabber_rules", 1));
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(automationRuleRepository.countByWorkspaceIdAndTypeInAndStatusNot(eq(workspaceId), any(), eq("archived")))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.ensureCanCreateGrabberRule(workspaceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, ProblemCodes.PLAN_LIMIT_EXCEEDED));
    }

    @Test
    void storyMonthLimitEnforced() {
        WorkspaceEntity workspace = workspace(planId, "active");
        PlanEntity plan = plan(Map.of("stories_month", 2));
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(postRepository.countByWorkspaceIdAndContentKindAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                        eq(workspaceId), eq("story"), any()))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.ensureCanCreateStory(workspaceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, ProblemCodes.PLAN_LIMIT_EXCEEDED));
    }

    private static void assertThatCode(ApiException ex, String code) {
        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(code);
    }

    private WorkspaceEntity workspace(UUID planId, String billingStatus) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setPlanId(planId);
        workspace.setBillingStatus(billingStatus);
        workspace.setName("ws");
        workspace.setOwnerUserId(UuidV7.generate());
        return workspace;
    }

    private static PlanEntity plan(Map<String, Object> limits) {
        PlanEntity plan = new PlanEntity();
        plan.setId(UuidV7.generate());
        plan.setCode("free");
        plan.setName("Free");
        plan.setLimits(limits);
        return plan;
    }
}
