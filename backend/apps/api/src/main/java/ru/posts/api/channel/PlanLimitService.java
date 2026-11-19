package ru.posts.api.channel;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.UsageCounterEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.InvitationRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.UsageCounterRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@Service
@Profile("!openapi")
@Transactional(readOnly = true)
public class PlanLimitService {

    private static final List<String> GRABBER_TYPES = List.of("grabber", "rss", "website");

    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final ChannelRepository channelRepository;
    private final PostRepository postRepository;
    private final UsageCounterRepository usageCounterRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final InvitationRepository invitationRepository;
    private final AutomationRuleRepository automationRuleRepository;
    private final ru.posts.persistence.repository.CrosspostRuleRepository crosspostRuleRepository;

    public PlanLimitService(
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            ChannelRepository channelRepository,
            PostRepository postRepository,
            UsageCounterRepository usageCounterRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            InvitationRepository invitationRepository,
            AutomationRuleRepository automationRuleRepository,
            ru.posts.persistence.repository.CrosspostRuleRepository crosspostRuleRepository) {
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.channelRepository = channelRepository;
        this.postRepository = postRepository;
        this.usageCounterRepository = usageCounterRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.invitationRepository = invitationRepository;
        this.automationRuleRepository = automationRuleRepository;
        this.crosspostRuleRepository = crosspostRuleRepository;
    }

    public void ensureNotFrozen(UUID workspaceId) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        if ("frozen".equals(workspace.getBillingStatus())) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.BILLING_FROZEN,
                    "Workspace billing is frozen",
                    Map.of("billing_status", "frozen"));
        }
    }

    public void ensureCanAddChannels(UUID workspaceId, int additional) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        int limit = channelLimit(workspace);
        long current = channelRepository.countActiveByWorkspaceId(workspaceId);
        if (current + additional > limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace channel limit reached",
                    Map.of("limit", "channels", "channels", limit, "current", current));
        }
    }

    public void ensureCanAddSeat(UUID workspaceId, boolean includePendingInvites) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        int seats = intLimit(workspace, "seats", 1);
        long members = workspaceMemberRepository.countByWorkspaceId(workspaceId);
        long pending = includePendingInvites ? invitationRepository.countPendingByWorkspaceId(workspaceId) : 0L;
        if (members + pending >= seats) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace seat limit reached",
                    Map.of("limit", "seats", "seats", seats, "current", members + pending));
        }
    }

    public void ensureCanCreateGrabberRule(UUID workspaceId) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        Integer limit = nullableIntLimit(workspace, "grabber_rules");
        if (limit == null) {
            return;
        }
        long current = automationRuleRepository.countByWorkspaceIdAndTypeInAndStatusNot(
                workspaceId, GRABBER_TYPES, "archived");
        if (current >= limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace grabber rule limit reached",
                    Map.of("limit", "grabber_rules", "grabber_rules", limit, "current", current));
        }
    }

    public void ensureCanCreateCrosspostRule(UUID workspaceId) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        Integer limit = nullableIntLimit(workspace, "crosspost_rules");
        if (limit == null) {
            return;
        }
        long current = crosspostRuleRepository.countByWorkspaceIdAndStatusIn(
                workspaceId, List.of("draft", "active", "paused", "auto_paused"));
        if (current >= limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace crosspost rule limit reached",
                    Map.of("limit", "crosspost_rules", "crosspost_rules", limit, "current", current));
        }
    }

    public void ensureCanCreateStory(UUID workspaceId) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        Integer limit = nullableIntLimit(workspace, "stories_month");
        if (limit == null) {
            return;
        }
        Instant monthStart =
                YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long current = postRepository.countByWorkspaceIdAndContentKindAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                workspaceId, "story", monthStart);
        if (current >= limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace monthly story limit reached",
                    Map.of("limit", "stories_month", "stories_month", limit, "current", current));
        }
    }

    public int channelLimit(UUID workspaceId) {
        return channelLimit(requireWorkspace(workspaceId));
    }

    private int channelLimit(WorkspaceEntity workspace) {
        return intLimit(workspace, "channels", 3);
    }

    /** Enforces {@code plans.limits.posts_month} when set (null = unlimited). */
    public void ensureCanCreatePost(UUID workspaceId) {
        ensureNotFrozen(workspaceId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        Integer limit = postsMonthLimit(workspace);
        if (limit == null) {
            return;
        }
        Instant monthStart =
                YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long current = postRepository.countByWorkspaceIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                workspaceId, monthStart);
        if (current >= limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace monthly post limit reached",
                    Map.of("limit", "posts_month", "posts_month", limit, "current", current));
        }
    }

    @Transactional
    public void recordPostCreated(UUID workspaceId) {
        String period = YearMonth.now(ZoneOffset.UTC).toString();
        UsageCounterEntity.UsageCounterId id = new UsageCounterEntity.UsageCounterId(workspaceId, period, "posts");
        UsageCounterEntity counter = usageCounterRepository.findById(id).orElseGet(() -> {
            UsageCounterEntity created = new UsageCounterEntity();
            created.setWorkspaceId(workspaceId);
            created.setPeriod(period);
            created.setMetric("posts");
            created.setValue(0);
            created.setUpdatedAt(Instant.now());
            return created;
        });
        counter.setValue(counter.getValue() + 1);
        counter.setUpdatedAt(Instant.now());
        usageCounterRepository.save(counter);
    }

    private Integer postsMonthLimit(WorkspaceEntity workspace) {
        return nullableIntLimit(workspace, "posts_month", 30);
    }

    private int intLimit(WorkspaceEntity workspace, String key, int defaultValue) {
        Integer value = nullableIntLimit(workspace, key);
        return value == null ? defaultValue : value;
    }

    private Integer nullableIntLimit(WorkspaceEntity workspace, String key) {
        return nullableIntLimit(workspace, key, null);
    }

    private Integer nullableIntLimit(WorkspaceEntity workspace, String key, Integer missingPlanDefault) {
        if (workspace.getPlanId() == null) {
            return missingPlanDefault;
        }
        PlanEntity plan = planRepository.findById(workspace.getPlanId()).orElse(null);
        if (plan == null || plan.getLimits() == null) {
            return missingPlanDefault;
        }
        Object raw = plan.getLimits().get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private WorkspaceEntity requireWorkspace(UUID workspaceId) {
        return workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Workspace not found"));
    }
}
