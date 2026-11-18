package ru.posts.api.admin;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.repository.UserRepository;

@Component
@Profile("!openapi")
public class PlatformAdminGuard {

    private final UserRepository userRepository;

    public PlatformAdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity requirePlatformAdmin(UUID userId) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (!user.isPlatformAdmin() || user.getDeletedAt() != null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ProblemCodes.PLATFORM_ADMIN_REQUIRED, "Platform admin required");
        }
        return user;
    }
}
