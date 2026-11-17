package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.channel.NetworkCapabilitiesService;
import ru.posts.api.web.dto.network.NetworkDtos;

@RestController
@RequestMapping(path = "/api/v1/networks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Networks", description = "Network capability matrix for validation and UI")
@OpenApiResponses
public class NetworkCapabilitiesController {

    private final NetworkCapabilitiesService networkCapabilitiesService;

    public NetworkCapabilitiesController(NetworkCapabilitiesService networkCapabilitiesService) {
        this.networkCapabilitiesService = networkCapabilitiesService;
    }

    @GetMapping("/capabilities")
    @Operation(
            operationId = "networksCapabilities",
            summary = "Get network capabilities snapshot",
            description =
                    "Machine-readable limits/features sourced from docs/api/fixtures/network-capabilities.yaml (AN-2). "
                            + "Network ids use DB keys: vk, tg, ok, max, rutube (YAML `telegram` maps to `tg`).")
    @ApiResponse(
            responseCode = "200",
            description = "Capabilities",
            content = @Content(schema = @Schema(implementation = NetworkDtos.NetworkCapabilitiesResponse.class)))
    public NetworkDtos.NetworkCapabilitiesResponse capabilities() {
        return networkCapabilitiesService.get();
    }
}
