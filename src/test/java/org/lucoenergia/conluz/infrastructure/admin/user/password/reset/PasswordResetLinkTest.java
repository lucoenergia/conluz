package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetLinkTest {

    private static final RawOneTimeToken TOKEN = new RawOneTimeToken("Ab_9-xY");

    @ParameterizedTest
    @CsvSource({
            "https://app.example.org, https://app.example.org/reset-password#Ab_9-xY",
            "https://app.example.org/, https://app.example.org/reset-password#Ab_9-xY",
            "https://app.example.org//, https://app.example.org/reset-password#Ab_9-xY",
            "' https://app.example.org ', https://app.example.org/reset-password#Ab_9-xY",
            "http://localhost:3001, http://localhost:3001/reset-password#Ab_9-xY",
            "https://example.org/conluz/, https://example.org/conluz/reset-password#Ab_9-xY",
            "HTTPS://app.example.org, HTTPS://app.example.org/reset-password#Ab_9-xY"
    })
    void putsTheTokenInTheFragment_withoutTheTrailingSlashes(String publicUrl, String expected) {
        PasswordResetLink link = PasswordResetLink.of(publicUrl);

        assertThat(link.isConfigured()).isTrue();
        assertThat(link.forToken(TOKEN)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "app.example.org",
            "/reset",
            "ftp://app.example.org",
            "javascript:alert(1)",
            "https://",
            "https://app.example.org/?x=1",
            "https://app.example.org/#top",
            "https://app example.org"
    })
    void anythingButAnAbsoluteHttpUrl_isNotConfigured(String publicUrl) {
        PasswordResetLink link = PasswordResetLink.of(publicUrl);

        assertThat(link.isConfigured()).isFalse();
        assertThat(link.forToken(TOKEN)).isEmpty();
    }
}
