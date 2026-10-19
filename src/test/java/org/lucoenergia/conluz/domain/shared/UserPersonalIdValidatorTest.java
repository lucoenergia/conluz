package org.lucoenergia.conluz.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The examples are worked out by hand from the published rules, and the CIFs {@code A08000143},
 * {@code A28015865} and {@code Q2826000H} are real ones, so the tests do not depend on the validator's own
 * arithmetic.
 */
class UserPersonalIdValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"12345678Z", "00000000T", "99999999R"})
    @DisplayName("ACC-010 a DNI with its control letter is valid")
    void aDniWithItsLetter_isValid(String dni) {
        assertThat(UserPersonalIdValidator.isValid(dni)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"X1234567L", "Y1234567X", "Z1234567R"})
    @DisplayName("ACC-010 an NIE starting with X, Y or Z with its control letter is valid")
    void anNieWithItsLetter_isValid(String nie) {
        assertThat(UserPersonalIdValidator.isValid(nie)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"K1234567L", "L1234567L", "M1234567L"})
    @DisplayName("ACC-010 a NIF starting with K, L or M with the control letter of its 7 digits is valid")
    void aPersonalNifWithItsLetter_isValid(String nif) {
        assertThat(UserPersonalIdValidator.isValid(nif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A08000143", "A28015865", "A58818501", "B12345674", "E12345674", "H12345674"})
    @DisplayName("ACC-010 a CIF whose organisation letter requires a digit is valid with its control digit")
    void aCifRequiringADigit_isValidWithItsDigit(String cif) {
        assertThat(UserPersonalIdValidator.isValid(cif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q2826000H", "P1234567D", "N0012345E", "R1234567D", "S1234567D", "W1234567D"})
    @DisplayName("ACC-010 a CIF whose organisation letter requires a letter is valid with its control letter")
    void aCifRequiringALetter_isValidWithItsLetter(String cif) {
        assertThat(UserPersonalIdValidator.isValid(cif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"G12345674", "G1234567D", "J99999997", "J9999999G", "C12345674", "C1234567D"})
    @DisplayName("ACC-010 a CIF whose organisation letter accepts either is valid with its control digit or letter")
    void aCifAcceptingEither_isValidWithItsDigitOrLetter(String cif) {
        assertThat(UserPersonalIdValidator.isValid(cif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678A", "X1234567A", "Y1234567L", "Z1234567L", "K1234567A", "M1234567Z",
            "A58818502", "Q2826000J", "G12345675", "G1234567E"})
    @DisplayName("ACC-010 a wrong control letter or character is invalid, for every form")
    void aWrongControl_isInvalid(String id) {
        assertThat(UserPersonalIdValidator.isValid(id)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567Z", "123456789Z", "X123456L", "X12345678L", "K123456L", "A5881850", "A588185011"})
    @DisplayName("ACC-010 an identifier of the wrong length is invalid")
    void aWrongLength_isInvalid(String id) {
        assertThat(UserPersonalIdValidator.isValid(id)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"I1234567L", "O1234567L", "T1234567L", "Ñ1234567L", "I12345674"})
    @DisplayName("ACC-010 an identifier with an unknown prefix is invalid")
    void anUnknownPrefix_isInvalid(String id) {
        assertThat(UserPersonalIdValidator.isValid(id)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234A678Z", "X12B4567L", "K12B4567L", "A58B18501", "ABCDEFGHJ", "１２３４５６７８Z"})
    @DisplayName("ACC-010 an identifier with a letter or a non-ASCII digit where an ASCII digit belongs is invalid")
    void aLetterWhereADigitBelongs_isInvalid(String id) {
        assertThat(UserPersonalIdValidator.isValid(id)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q28260008", "P12345674", "N00123455"})
    @DisplayName("ACC-010 a CIF with a control digit where its organisation letter requires a letter is invalid")
    void aCifWithADigitWhereALetterIsRequired_isInvalid(String cif) {
        assertThat(UserPersonalIdValidator.isValid(cif)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A5881850A", "B1234567D", "H1234567D"})
    @DisplayName("ACC-010 a CIF with a control letter where its organisation letter requires a digit is invalid")
    void aCifWithALetterWhereADigitIsRequired_isInvalid(String cif) {
        assertThat(UserPersonalIdValidator.isValid(cif)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678z", "12.345.678-Z", " 12345678Z", "x1234567l"})
    @DisplayName("ACC-010 the validator takes the normalised value: a value not yet normalised is invalid")
    void aValueNotYetNormalised_isInvalid(String id) {
        assertThat(UserPersonalIdValidator.isValid(id)).isFalse();
    }

    @Test
    @DisplayName("ACC-010 null and the empty string are invalid")
    void nullAndEmpty_areInvalid() {
        assertThat(UserPersonalIdValidator.isValid(null)).isFalse();
        assertThat(UserPersonalIdValidator.isValid("")).isFalse();
    }
}
