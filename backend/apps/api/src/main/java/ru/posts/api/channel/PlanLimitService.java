package ru.posts.api.channel;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@Service
@Profile("!openapi")
@Transactional(readOnly = true)
public class PlanLimitService {

    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final ChannelRepository channelRepository;

    public PlanLimitService(
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            ChannelRepository channelRepository) {
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.channelRepository = channelRepository;
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
}
