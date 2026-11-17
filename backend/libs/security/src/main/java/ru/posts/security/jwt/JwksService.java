package ru.posts.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;

public final class JwksService {

    private final EcKeyProvider keyProvider;

    public JwksService(EcKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public Map<String, Object> jwks() {
        return new JWKSet(keyProvider.publicJwk()).toJSONObject();
    }
}
