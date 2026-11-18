package ru.posts.api.channel;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.UsageCounterRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@Service
@Profile("!openapi")
@Transactional(readOnly = true)
public class PlanLimitService {

    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final ChannelRepository channelRepository;
    private final PostRepository postRepository;
    private final UsageCounterRepository usageCounterRepository;

    public PlanLimitService(
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            ChannelRepository channelRepository,
            PostRepository postRepository,
            UsageCounterRepository usageCounterRepository) {
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.channelRepository = channelRepository;
        this.postRepository = postRepository;
        this.usageCounterRepository = usageCounterRepository;
    }

    public void ensureCanAddChannels(UUID workspaceId, int additional) {
        WorkspaceEntity workspace = workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Workspace not found"));
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

    public int channelLimit(UUID workspaceId) {
        WorkspaceEntity workspace = workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Workspace not found"));
        return channelLimit(workspace);
    }

    private int channelLimit(WorkspaceEntity workspace) {
        if (workspace.getPlanId() == null) {
            return 3;
        }
        PlanEntity plan = planRepository.findById(workspace.getPlanId()).orElse(null);
        if (plan == null || plan.getLimits() == null) {
            return 3;
        }
        Object channels = plan.getLimits().get("channels");
        if (channels instanceof Number number) {
            return number.intValue();
        }
        return 3;
    }

    /** Enforces {@code plans.limits.posts_month} when set (null = unlimited). */
    public void ensureCanCreatePost(UUID workspaceId) {
        WorkspaceEntity workspace = workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Workspace not found"));
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
        if (workspace.getPlanId() == null) {
            return 30;
        }
        PlanEntity plan = planRepository.findById(workspace.getPlanId()).orElse(null);
        if (plan == null || plan.getLimits() == null) {
            return 30;
        }
        Object postsMonth = plan.getLimits().get("posts_month");
        if (postsMonth == null) {
            return null;
        }
        if (postsMonth instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
