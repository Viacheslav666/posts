package ru.posts.api.ord;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.ord.api.OrdProvider;
import ru.posts.ord.api.OrdProviderId;

/** Routes to {@link OrdProvider} beans by {@link OrdProviderId}. */
@Component
@Profile("!openapi")
public class OrdProviderRegistry {

    private final Map<OrdProviderId, OrdProvider> byId;

    public OrdProviderRegistry(List<OrdProvider> providers) {
        Map<OrdProviderId, OrdProvider> map = new EnumMap<>(OrdProviderId.class);
        for (OrdProvider provider : providers) {
            map.put(provider.providerId(), provider);
        }
        this.byId = Map.copyOf(map);
    }

    public OrdProvider require(OrdProviderId id) {
        OrdProvider provider = byId.get(id);
        if (provider == null) {
            throw new IllegalArgumentException("ORD provider not registered: " + id);
        }
        return provider;
    }

    public OrdProvider require(String code) {
        return require(OrdProviderId.fromCode(code));
    }
}
