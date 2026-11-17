package ru.posts.security.jwt;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads or generates the ES256 key pair used for access JWTs. */
public final class EcKeyProvider {

    private final ECKey ecKey;

    public EcKeyProvider(JwtProperties properties) {
        try {
            if (properties.getPrivateKeyPem() != null
                    && !properties.getPrivateKeyPem().isBlank()) {
                ECPrivateKey privateKey = parsePrivateKey(properties.getPrivateKeyPem());
                ECPublicKey publicKey;
                if (properties.getPublicKeyPem() != null
                        && !properties.getPublicKeyPem().isBlank()) {
                    publicKey = parsePublicKey(properties.getPublicKeyPem());
                } else {
                    throw new IllegalStateException("posts.auth.jwt.public-key-pem is required with private-key-pem");
                }
                this.ecKey = new ECKey.Builder(Curve.P_256, publicKey)
                        .privateKey(privateKey)
                        .keyID(properties.getKeyId())
                        .build();
            } else {
                this.ecKey = new ECKeyGenerator(Curve.P_256)
                        .keyID(properties.getKeyId())
                        .generate();
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize ES256 key", ex);
        }
    }

    public ECKey ecKey() {
        return ecKey;
    }

    public ECKey publicJwk() {
        return ecKey.toPublicJWK();
    }

    private static ECPrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePem(pem, "PRIVATE KEY");
        KeyFactory factory = KeyFactory.getInstance("EC");
        return (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static ECPublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = decodePem(pem, "PUBLIC KEY");
        KeyFactory factory = KeyFactory.getInstance("EC");
        return (ECPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] decodePem(String pem, String type) {
        String normalized = pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
