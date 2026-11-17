package ru.posts.emulator.control;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import ru.posts.emulator.store.Network;

@Service
public class ScenarioService {

    private final AtomicReference<ScenarioConfig> config = new AtomicReference<>(ScenarioConfig.defaults());

    public ScenarioConfig get() {
        return config.get().copy();
    }

    public ScenarioConfig update(ScenarioConfig update) {
        ScenarioConfig next = update == null ? ScenarioConfig.defaults() : update.copy();
        // ensure all networks present
        for (Network network : Network.values()) {
            next.forNetwork(network);
        }
        config.set(next);
        return get();
    }

    public void reset() {
        config.set(ScenarioConfig.defaults());
    }

    public NetworkScenario network(Network network) {
        return config.get().forNetwork(network).copy();
    }

    public long globalDelayMs() {
        return config.get().getGlobalDelayMs();
    }
}
