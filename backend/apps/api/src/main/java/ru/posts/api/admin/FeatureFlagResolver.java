package ru.posts.api.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.FeatureFlagEntity;
import ru.posts.persistence.entity.WorkspaceFeatureFlagEntity;
import ru.posts.persistence.repository.FeatureFlagRepository;
import ru.posts.persistence.repository.WorkspaceFeatureFlagRepository;

@Component
@Profile("!openapi")
public class FeatureFlagResolver {

    private final FeatureFlagRepository featureFlagRepository;
    private final WorkspaceFeatureFlagRepository workspaceFeatureFlagRepository;

    public FeatureFlagResolver(
            FeatureFlagRepository featureFlagRepository,
            WorkspaceFeatureFlagRepository workspaceFeatureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
        this.workspaceFeatureFlagRepository = workspaceFeatureFlagRepository;
    }

    /** Global defaults for {@code GET /me.features}. */
    public Map<String, Boolean> globalFeatures() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FeatureFlagEntity flag : featureFlagRepository.findAllByOrderByKeyAsc()) {
            out.put(flag.getKey(), flag.isEnabledGlobal());
        }
        return out;
    }

    public Map<String, Boolean> resolveForWorkspace(UUID workspaceId) {
        Map<String, Boolean> out = globalFeatures();
        List<WorkspaceFeatureFlagEntity> overrides = workspaceFeatureFlagRepository.findByWorkspaceId(workspaceId);
        for (WorkspaceFeatureFlagEntity override : overrides) {
            out.put(override.getFlagKey(), override.isEnabled());
        }
        return out;
    }
}
