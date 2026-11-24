package ru.posts.api.web.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.user.UserDtos;

/** Deterministic sample data for the `local` Spring profile. */
public final class LocalFixtures {

    public static final UUID LOCAL_USER_ID = UUID.fromString("019a0000-0002-7000-8000-000000000001");
    public static final UUID LOCAL_WORKSPACE_ID = UUID.fromString("019a0000-0002-7000-8000-000000000002");

    private LocalFixtures() {}

    public static UserDtos.UserProfile localUser() {
        return new UserDtos.UserProfile(
                LOCAL_USER_ID,
                "local@posts.test",
                true,
                "Local Dev",
                null,
                "email",
                "Europe/Moscow",
                "ru",
                false,
                Instant.parse("2026-11-01T10:00:00Z"),
                List.of(new UserDtos.MembershipSummary(
                        LOCAL_WORKSPACE_ID,
                        "Личное пространство",
                        ApiEnums.MemberRole.owner,
                        ApiEnums.BillingStatus.trial)),
                false,
                Map.of(),
                null,
                List.of());
    }
}
