package ru.posts.api.crosspost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.channel.PlanLimitService;
import ru.posts.api.error.ApiException;
import ru.posts.api.web.dto.crosspost.CrosspostDtos;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.crosspost.process.CrosspostItemProcessor;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.CrosspostRuleTargetRepository;
import ru.posts.security.rbac.RbacRole;
import ru.posts.social.inbound.subscription.EventSubscriptionService;

@ExtendWith(MockitoExtension.class)
class CrosspostServiceIdorTest {

    @Mock
    CrosspostRuleRepository ruleRepository;

    @Mock
    CrosspostRuleTargetRepository targetRepository;

    @Mock
    CrosspostItemRepository itemRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    WorkspaceAccessService access;

    @Mock
    PlanLimitService planLimitService;

    @Mock
    FeatureFlagResolver featureFlagResolver;

    @Mock
    EventSubscriptionService subscriptionService;

    @Mock
    CrosspostItemProcessor itemProcessor;

    CrosspostServiceImpl service;
    UUID workspaceId;
    UUID otherWorkspace;
    UUID userId;
    UUID projectId;

    @BeforeEach
    void setUp() {
        service = new CrosspostServiceImpl(
                ruleRepository,
                targetRepository,
                itemRepository,
                channelRepository,
                access,
                planLimitService,
                featureFlagResolver,
                subscriptionService,
                itemProcessor);
        workspaceId = UuidV7.generate();
        otherWorkspace = UuidV7.generate();
        userId = UuidV7.generate();
        projectId = UuidV7.generate();
    }

    @Test
    void createRejectsForeignChannelAs404() {
        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, RbacRole.owner, Set.of());
        when(access.requirePermission(workspaceId, userId, "automations.manage"))
                .thenReturn(membership);
        when(featureFlagResolver.resolveForWorkspace(workspaceId)).thenReturn(Map.of("automation.crosspost", true));
        ChannelEntity foreign = new ChannelEntity();
        foreign.setId(UuidV7.generate());
        foreign.setWorkspaceId(otherWorkspace);
        foreign.setNetwork("vk");
        when(channelRepository.findByIdAndDeletedAtIsNull(foreign.getId())).thenReturn(Optional.of(foreign));

        CrosspostDtos.RuleCreate req = new CrosspostDtos.RuleCreate("r", foreign.getId(), Map.of(), List.of(), false);

        assertThatThrownBy(() -> service.createRule(userId, workspaceId, projectId, req, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void journalPageUsesTheCursorOffsetInsteadOfSlicingTheFirstPage() {
        UUID ruleId = UuidV7.generate();
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(ruleId);
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(access.requirePermission(workspaceId, userId, "automations.view"))
                .thenReturn(new WorkspaceMembership(workspaceId, userId, RbacRole.owner, Set.of()));
        when(itemRepository.pageByRule(eq(ruleId), isNull(), eq(51), eq(50))).thenReturn(List.of());

        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString("50".getBytes(StandardCharsets.UTF_8));
        service.listItems(userId, ruleId, null, cursor, 50);

        verify(itemRepository).pageByRule(ruleId, null, 51, 50);
    }
}
