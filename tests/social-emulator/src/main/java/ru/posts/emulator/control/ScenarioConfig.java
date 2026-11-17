package ru.posts.emulator.control;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.posts.emulator.store.Network;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioConfig {

    @Min(0)
    private long globalDelayMs;

    @Valid
    private Map<String, NetworkScenario> networks = defaultNetworks();

    public long getGlobalDelayMs() {
        return globalDelayMs;
    }

    public void setGlobalDelayMs(long globalDelayMs) {
        this.globalDelayMs = globalDelayMs;
    }

    public Map<String, NetworkScenario> getNetworks() {
        return networks;
    }

    public void setNetworks(Map<String, NetworkScenario> networks) {
        this.networks = networks == null ? defaultNetworks() : networks;
    }

    public NetworkScenario forNetwork(Network network) {
        NetworkScenario scenario = networks.get(network.id());
        if (scenario == null) {
            scenario = new NetworkScenario();
            networks.put(network.id(), scenario);
        }
        return scenario;
    }

    public ScenarioConfig copy() {
        ScenarioConfig copy = new ScenarioConfig();
        copy.globalDelayMs = globalDelayMs;
        Map<String, NetworkScenario> networkCopy = new LinkedHashMap<>();
        for (Map.Entry<String, NetworkScenario> entry : networks.entrySet()) {
            networkCopy.put(
                    entry.getKey(),
                    entry.getValue() == null
                            ? new NetworkScenario()
                            : entry.getValue().copy());
        }
        copy.networks = networkCopy;
        return copy;
    }

    public static ScenarioConfig defaults() {
        return new ScenarioConfig();
    }

    private static Map<String, NetworkScenario> defaultNetworks() {
        Map<String, NetworkScenario> map = new LinkedHashMap<>();
        for (Network network : Network.values()) {
            map.put(network.id(), new NetworkScenario());
        }
        return map;
    }
}
