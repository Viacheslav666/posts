package ru.posts.api.web.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {}

    @Schema(name = "Project")
    public record Project(
            UUID id,
            UUID workspaceId,
            @Schema(example = "Клиент А") String name,
            @Schema(example = "#3B82F6") String color,
            Instant createdAt) {}

    @Schema(name = "CreateProjectRequest")
    public record CreateProjectRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") @Schema(example = "#3B82F6") String color) {}

    @Schema(name = "UpdateProjectRequest")
    public record UpdateProjectRequest(
            @Size(min = 1, max = 120) String name, @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {}

    @Schema(name = "ProjectMembersRequest")
    public record ProjectMembersRequest(@Schema(description = "User IDs to grant project access") List<UUID> userIds) {}
}
