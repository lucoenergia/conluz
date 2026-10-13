package org.lucoenergia.conluz.infrastructure.shared.personalid;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotEmptyOnceNormalisedValidatorTest {

    private final NotEmptyOnceNormalisedValidator validator = new NotEmptyOnceNormalisedValidator();

    @ParameterizedTest
    @ValueSource(strings = {"", "..-", " . ", "\u00A0-\t", "---"})
    void aValueMadeOnlyOfRemovedCharacters_isInvalid(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678Z", "12.345.678-z", " x ", "-1-"})
    void aValueWithAnyOtherCharacter_isValid(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @Test
    void null_isLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
