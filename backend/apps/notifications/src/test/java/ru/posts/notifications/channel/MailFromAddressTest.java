package ru.posts.notifications.channel;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;

class MailFromAddressTest {

    @Test
    void displayNameIsNotDoubleWrapped() throws Exception {
        MailFromAddress.Parsed parsed = MailFromAddress.parse("Posts <noreply@socialposts.ru>", "Posts");
        assertThat(parsed.address()).isEqualTo("noreply@socialposts.ru");
        assertThat(parsed.personal()).isEqualTo("Posts");

        InternetAddress formatted = new InternetAddress(parsed.address(), parsed.personal(), "UTF-8");
        assertThat(formatted.toString()).doesNotContain("Posts <Posts");
        assertThat(formatted.getAddress()).isEqualTo("noreply@socialposts.ru");
        assertThat(formatted.getPersonal()).isEqualTo("Posts");
    }

    @Test
    void bareAddressUsesFallbackDisplayName() {
        MailFromAddress.Parsed parsed = MailFromAddress.parse("noreply@socialposts.ru", "Posts");
        assertThat(parsed.address()).isEqualTo("noreply@socialposts.ru");
        assertThat(parsed.personal()).isEqualTo("Posts");
    }

    @Test
    void quotedDisplayNameIsKept() {
        MailFromAddress.Parsed parsed = MailFromAddress.parse("\"Posts Team\" <noreply@socialposts.ru>", "Other");
        assertThat(parsed.address()).isEqualTo("noreply@socialposts.ru");
        assertThat(parsed.personal()).isEqualTo("Posts Team");
    }
}
