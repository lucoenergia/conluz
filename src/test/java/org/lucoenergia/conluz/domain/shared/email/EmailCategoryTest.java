package org.lucoenergia.conluz.domain.shared.email;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailCategoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"PASSWORD_RESET", "INVITATION", "A", "STEP_2"})
    void acceptsUpperCaseLabels(String value) {
        assertThat(new EmailCategory(value)).hasToString(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "password_reset", "user@example.org", "PASSWORD RESET", "_RESET", "2FA",
            "A_LABEL_THAT_IS_LONGER_THAN_FORTY_CHARACTERS"})
    void rejectsAnythingElse(String value) {
        assertThatThrownBy(() -> new EmailCategory(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
