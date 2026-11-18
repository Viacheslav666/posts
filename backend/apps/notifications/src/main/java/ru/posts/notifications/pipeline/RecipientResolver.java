package ru.posts.notifications.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;

@Component
public class RecipientResolver {

    private static final Set<String> OPERATIONAL_ROLES = Set.of("owner", "admin", "editor");

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public RecipientResolver(WorkspaceMemberRepository workspaceMemberRepository, UserRepository userRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    public List<UUID> resolve(NotifyDomainEvent event) {
        if (event == null || event.eventType() == null) {
            return List.of();
        }
        String type = event.eventType();
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();

        if (type.startsWith("invitation.")) {
            return resolveInvitee(payload);
        }

        UUID workspaceId = extractUuid(payload, "workspaceId");
        if (workspaceId == null) {
            workspaceId = extractUuid(payload, "workspace_id");
        }
        if (workspaceId == null && type.startsWith("team.")) {
            workspaceId = event.aggregateId();
        }
        if (workspaceId == null) {
            return List.of();
        }

        List<WorkspaceMemberEntity> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        List<UUID> recipients = new ArrayList<>();
        for (WorkspaceMemberEntity member : members) {
            if (member.getRole() != null
                    && OPERATIONAL_ROLES.contains(member.getRole().toLowerCase(Locale.ROOT))) {
                recipients.add(member.getUserId());
            }
        }

        UUID explicitUser = extractUuid(payload, "userId");
        if (explicitUser == null) {
            explicitUser = extractUuid(payload, "user_id");
        }
        if (explicitUser != null && !recipients.contains(explicitUser)) {
            recipients.add(explicitUser);
        }
        return List.copyOf(recipients);
    }

    public UUID resolveWorkspaceId(NotifyDomainEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        UUID workspaceId = extractUuid(payload, "workspaceId");
        if (workspaceId == null) {
            workspaceId = extractUuid(payload, "workspace_id");
        }
        return workspaceId;
    }

    private List<UUID> resolveInvitee(Map<String, Object> payload) {
        UUID inviteeUserId = extractUuid(payload, "inviteeUserId");
        if (inviteeUserId == null) {
            inviteeUserId = extractUuid(payload, "userId");
        }
        if (inviteeUserId != null) {
            return List.of(inviteeUserId);
        }
        String email = stringVal(payload, "email");
        if (email == null) {
            email = stringVal(payload, "inviteeEmail");
        }
        if (email == null || email.isBlank()) {
            return List.of();
        }
        Optional<UserEntity> user = userRepository.findByEmailIgnoreCase(email.trim());
        return user.map(u -> List.of(u.getId())).orElse(List.of());
    }

    static UUID extractUuid(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stringVal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
