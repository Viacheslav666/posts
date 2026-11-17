package ru.posts.security.totp;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TotpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String otpauthUrl(String issuer, String account, String secret) {
        QrData data = new QrData.Builder()
                .label(account)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code.trim());
    }

    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(randomBackupCode());
        }
        return codes;
    }

    private static String randomBackupCode() {
        int value = RANDOM.nextInt(100_000_000);
        return String.format(Locale.ROOT, "%08d", value);
    }
}
