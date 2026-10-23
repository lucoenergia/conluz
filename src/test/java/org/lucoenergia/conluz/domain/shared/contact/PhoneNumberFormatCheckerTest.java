package org.lucoenergia.conluz.domain.shared.contact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberFormatCheckerTest {

    @ParameterizedTest
    @ValueSource(strings = {"600000", "+34 600 000 000", "976 00 00 00", "+346000000"})
    void digitsSpacesAndALeadingPlusWithAtLeastSixDigitsAreValid(String phone) {
        assertThat(PhoneNumberFormatChecker.check(phone)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"600 CALL ME", "600-000-000", "(976) 000 000", "34+600000000", "++34600000000"})
    void anythingButDigitsSpacesAndALeadingPlusBreaksTheCharacters(String phone) {
        assertThat(PhoneNumberFormatChecker.check(phone)).contains(PhoneNumberFormatChecker.Violation.CHARACTERS_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"60000", "+34 12 3", "+", ""})
    void fewerThanSixDigitsAreTooFew(String phone) {
        assertThat(PhoneNumberFormatChecker.check(phone)).contains(PhoneNumberFormatChecker.Violation.TOO_FEW_DIGITS);
    }

    @Test
    void theCharactersAreCheckedBeforeTheDigitsAreCounted() {
        assertThat(PhoneNumberFormatChecker.check("12a")).contains(PhoneNumberFormatChecker.Violation.CHARACTERS_INVALID);
    }
}
