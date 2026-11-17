package ru.posts.emulator.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.posts.emulator.control.ScenarioEngine;
import ru.posts.emulator.store.Network;

@Component
public class PublishSupport {

    private final ScenarioEngine scenarioEngine;

    public PublishSupport(ScenarioEngine scenarioEngine) {
        this.scenarioEngine = scenarioEngine;
    }

    public ResponseEntity<?> execute(
            Network network, HttpServletResponse rawResponse, Supplier<ResponseEntity<?>> successBody)
            throws IOException {
        ScenarioEngine.Outcome outcome = scenarioEngine.beforePublish(network);
        return switch (outcome) {
            case ScenarioEngine.Outcome.Respond respond -> respond.response();
            case ScenarioEngine.Outcome.DropConnection ignored -> {
                ScenarioEngine.abort(rawResponse);
                yield ResponseEntity.status(499).build();
            }
            case ScenarioEngine.Outcome.CreateThenDrop ignored -> {
                successBody.get();
                ScenarioEngine.abort(rawResponse);
                yield ResponseEntity.status(499).build();
            }
            case ScenarioEngine.Outcome.Proceed ignored -> successBody.get();
        };
    }
}
