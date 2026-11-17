package ru.posts.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenCryptoServiceTest {

    @Test
    void sealUnsealRoundTrip() {
        TokenCryptoService crypto = TokenCryptoService.fromKeyMaterial("test-kek-material");
        byte[] dek = crypto.generateDek();
        byte[] wrapped = crypto.wrapDek(dek);
        byte[] unwrapped = crypto.unwrapDek(wrapped);
        assertThat(unwrapped).isEqualTo(dek);

        byte[] sealed = crypto.seal(dek, "vk-access-token-secret");
        assertThat(sealed[0]).isEqualTo(TokenCryptoService.BLOB_VERSION);
        assertThat(new String(sealed, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("vk-access-token-secret");
        assertThat(crypto.unseal(dek, sealed)).isEqualTo("vk-access-token-secret");
    }

    @Test
    void differentDekCannotDecrypt() {
        TokenCryptoService crypto = TokenCryptoService.fromKeyMaterial("test-kek-material");
        byte[] dek1 = crypto.generateDek();
        byte[] dek2 = crypto.generateDek();
        byte[] sealed = crypto.seal(dek1, "secret");
        assertThatThrownBy(() -> crypto.unseal(dek2, sealed)).isInstanceOf(IllegalStateException.class);
    }
}
