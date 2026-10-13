package org.lucoenergia.conluz.domain.shared.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionConfigCompletenessTest {

    private static final BaseUrlValidity VALID = baseUrl -> true;
    private static final BaseUrlValidity INVALID = baseUrl -> false;

    @Test
    void completeConfigurationMissesNothing() {
        assertThat(ConnectionConfigCompleteness.missingFields("user", "https://example.com", "secret", VALID))
                .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void blankUsernameIsMissing(String username) {
        assertThat(ConnectionConfigCompleteness.missingFields(username, "https://example.com", "secret", VALID))
                .containsExactly("username");
    }

    @Test
    void baseUrlTheValidityRejectsIsMissing() {
        assertThat(ConnectionConfigCompleteness.missingFields("user", "https://example.com", "secret", INVALID))
                .containsExactly("baseUrl");
    }

    @Test
    void baseUrlIsJudgedByTheValidityAlone() {
        BaseUrlValidity onlyThis = "accepted"::equals;

        assertThat(ConnectionConfigCompleteness.missingFields("user", "accepted", "secret", onlyThis)).isEmpty();
        assertThat(ConnectionConfigCompleteness.missingFields("user", "other", "secret", onlyThis))
                .containsExactly("baseUrl");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyPasswordIsMissing(String password) {
        assertThat(ConnectionConfigCompleteness.missingFields("user", "https://example.com", password, VALID))
                .containsExactly("password");
    }

    @Test
    void blankButNonEmptyPasswordIsNotMissing() {
        assertThat(ConnectionConfigCompleteness.missingFields("user", "https://example.com", " ", VALID)).isEmpty();
    }

    @Test
    void missingFieldsComeInFixedOrder() {
        assertThat(ConnectionConfigCompleteness.missingFields(null, "x", null, INVALID))
                .containsExactly("username", "baseUrl", "password");
        assertThat(ConnectionConfigCompleteness.missingFields(" ", "x", "secret", INVALID))
                .containsExactly("username", "baseUrl");
        assertThat(ConnectionConfigCompleteness.missingFields("user", "x", "", INVALID))
                .containsExactly("baseUrl", "password");
        assertThat(ConnectionConfigCompleteness.missingFields("", "https://example.com", "", VALID))
                .containsExactly("username", "password");
    }
}
