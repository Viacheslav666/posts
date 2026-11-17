package ru.posts.emulator.control;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.posts.emulator.store.Network;

@Component
public class ScenarioEngine {

    private final ScenarioService scenarioService;

    public ScenarioEngine(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    public sealed interface Outcome {
        /** Continue and return a normal success response. */
        record Proceed() implements Outcome {}

        /** Create the post (side effect), then drop the connection without a body. */
        record CreateThenDrop() implements Outcome {}

        record Respond(ResponseEntity<?> response) implements Outcome {}

        record DropConnection() implements Outcome {}
    }

    public Outcome beforePublish(Network network) {
        applyDelay(network);
        NetworkScenario scenario = scenarioService.network(network);
        ScenarioFault fault = resolveFault(scenario);

        return switch (fault) {
            case NONE -> new Outcome.Proceed();
            case HANG -> {
                sleep(scenario.getHangMs());
                yield new Outcome.DropConnection();
            }
            case DROP -> new Outcome.CreateThenDrop();
            case HTTP_5XX -> new Outcome.Respond(ResponseEntity.status(pick5xx(scenario))
                    .body(Map.of("error", "emulated_5xx", "network", network.id())));
            case HTTP_429 -> new Outcome.Respond(rateLimitResponse(network, scenario));
            case HTTP_FORBIDDEN -> new Outcome.Respond(forbiddenResponse(network, scenario));
            case VK_6, VK_9, VK_10, VK_15, VK_100 -> {
                if (network != Network.VK) {
                    // On non-VK networks VK_* codes map to permanent 403 (access denied semantics).
                    yield new Outcome.Respond(forbiddenResponse(network, scenario));
                }
                yield new Outcome.Respond(vkError(fault));
            }
        };
    }

    private ScenarioFault resolveFault(NetworkScenario scenario) {
        if (scenario.getForcedError() != null && !scenario.getForcedError().isBlank()) {
            return ScenarioFault.from(scenario.getForcedError());
        }
        if (scenario.getErrorRate() <= 0) {
            return ScenarioFault.NONE;
        }
        if (ThreadLocalRandom.current().nextDouble() < scenario.getErrorRate()) {
            if (scenario.getHttpStatus() != null && scenario.getHttpStatus() == 429) {
                return ScenarioFault.HTTP_429;
            }
            return ScenarioFault.HTTP_5XX;
        }
        return ScenarioFault.NONE;
    }

    private void applyDelay(Network network) {
        long delay = scenarioService.globalDelayMs()
                + scenarioService.network(network).getDelayMs();
        sleep(delay);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static int pick5xx(NetworkScenario scenario) {
        if (scenario.getHttpStatus() != null && scenario.getHttpStatus() >= 500) {
            return scenario.getHttpStatus();
        }
        return 503;
    }

    private static int pickForbidden(NetworkScenario scenario) {
        if (scenario.getHttpStatus() != null
                && scenario.getHttpStatus() >= 400
                && scenario.getHttpStatus() < 500
                && scenario.getHttpStatus() != 429) {
            return scenario.getHttpStatus();
        }
        return 403;
    }

    private ResponseEntity<?> forbiddenResponse(Network network, NetworkScenario scenario) {
        int status = pickForbidden(scenario);
        if (network == Network.TELEGRAM || network == Network.MAX) {
            return ResponseEntity.status(status)
                    .body(Map.of(
                            "ok",
                            false,
                            "error_code",
                            status,
                            "description",
                            "Forbidden: bot was kicked / no rights to post"));
        }
        return ResponseEntity.status(status)
                .body(Map.of("error", "forbidden", "network", network.id(), "httpStatus", status));
    }

    private ResponseEntity<?> rateLimitResponse(Network network, NetworkScenario scenario) {
        int retryAfter = scenario.getRetryAfterSeconds();
        if (network == Network.TELEGRAM || network == Network.MAX) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(Map.of(
                            "ok",
                            false,
                            "error_code",
                            429,
                            "description",
                            "Too Many Requests: retry after " + retryAfter,
                            "parameters",
                            Map.of("retry_after", retryAfter)));
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfter))
                .body(Map.of("error", "rate_limited", "retry_after", retryAfter, "network", network.id()));
    }

    private static ResponseEntity<?> vkError(ScenarioFault fault) {
        int code =
                switch (fault) {
                    case VK_6 -> 6;
                    case VK_9 -> 9;
                    case VK_10 -> 10;
                    case VK_15 -> 15;
                    case VK_100 -> 100;
                    default -> 10;
                };
        String message =
                switch (code) {
                    case 6 -> "Too many requests per second";
                    case 9 -> "Flood control: message sent too quickly";
                    case 10 -> "Internal server error";
                    case 15 -> "Access denied";
                    case 100 -> "One of the parameters specified was missing or invalid";
                    default -> "Emulated VK error";
                };
        return ResponseEntity.ok(Map.of(
                "error",
                Map.of(
                        "error_code", code,
                        "error_msg", message,
                        "request_params", java.util.List.of())));
    }

    public static void abort(HttpServletResponse response) throws IOException {
        try {
            response.resetBuffer();
            response.setStatus(499);
            response.flushBuffer();
            response.getOutputStream().close();
        } catch (IOException ignored) {
            // connection intentionally dropped
        }
    }
}
