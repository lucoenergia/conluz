package org.lucoenergia.conluz.domain.admin.user.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    /** U+1F600, outside the Basic Multilingual Plane: one code point, two Java chars, four UTF-8 bytes. */
    private static final String EMOJI = "😀";
    /** U+00E9: one code point, one Java char, two UTF-8 bytes. */
    private static final String E_ACUTE = "é";

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("ACC-021 nine code points are too short")
    void nineCodePointsAreTooShort() {
        assertRule(PasswordPolicyViolation.TOO_SHORT, "a".repeat(9));
    }

    @Test
    @DisplayName("ACC-021 ten code points are accepted")
    void tenCodePointsAreAccepted() {
        assertDoesNotThrow(() -> policy.check("a".repeat(10)));
    }

    @Test
    @DisplayName("ACC-021 sixty-four code points are accepted")
    void sixtyFourCodePointsAreAccepted() {
        assertDoesNotThrow(() -> policy.check("a".repeat(64)));
    }

    @Test
    @DisplayName("ACC-021 sixty-five code points are too long")
    void sixtyFiveCodePointsAreTooLong() {
        assertRule(PasswordPolicyViolation.TOO_LONG, "a".repeat(65));
    }

    @Test
    void seventyTwoBytesAreAccepted() {
        String password = E_ACUTE.repeat(36);
        assertEquals(72, password.getBytes(StandardCharsets.UTF_8).length);

        assertDoesNotThrow(() -> policy.check(password));
    }

    @Test
    @DisplayName("ACC-021 more than 72 bytes within 64 code points are too many bytes")
    void seventyThreeBytesAreTooManyBytes() {
        String password = E_ACUTE.repeat(36) + "a";
        assertEquals(73, password.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(37, password.codePointCount(0, password.length()));

        assertRule(PasswordPolicyViolation.TOO_MANY_BYTES, password);
    }

    @Test
    @DisplayName("ACC-021 ten characters outside the BMP are accepted, and nine are too short")
    void lengthIsCountedInCodePointsNotInJavaChars() {
        // 9 code points but 18 Java chars: counting chars would wrongly accept it
        assertRule(PasswordPolicyViolation.TOO_SHORT, EMOJI.repeat(9));
        // 10 code points, 40 bytes
        assertDoesNotThrow(() -> policy.check(EMOJI.repeat(10)));
    }

    @Test
    void charactersOutsideTheBmpCountFourBytesEach() {
        assertDoesNotThrow(() -> policy.check(EMOJI.repeat(18)));
        assertRule(PasswordPolicyViolation.TOO_MANY_BYTES, EMOJI.repeat(19));
    }

    @Test
    @DisplayName("ACC-021 too long is reported before too many bytes")
    void tooLongIsReportedBeforeTooManyBytes() {
        assertRule(PasswordPolicyViolation.TOO_LONG, "a".repeat(73));
    }

    @Test
    @DisplayName("ACC-021 the value is never transformed: surrounding spaces count")
    void surroundingSpacesAreNotTrimmed() {
        // 8 visible characters: trimming would make it too short
        assertDoesNotThrow(() -> policy.check(" " + "a".repeat(8) + " "));
        assertRule(PasswordPolicyViolation.TOO_SHORT, " " + "a".repeat(7) + " ");
    }

    @Test
    @DisplayName("ACC-021 any character is accepted: ten spaces are a password")
    void onlySpacesAreAcceptedWhenLongEnough() {
        assertDoesNotThrow(() -> policy.check(" ".repeat(10)));
    }

    @Test
    void noCompositionRulesApply() {
        assertDoesNotThrow(() -> policy.check("correct horse battery staple"));
        assertDoesNotThrow(() -> policy.check("onlylowercaselettershere"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("ACC-021 an absent password is too short")
    void absentPasswordsAreTooShort(String password) {
        assertRule(PasswordPolicyViolation.TOO_SHORT, password);
    }

    @Test
    void theExceptionNeverCarriesThePassword() {
        String password = "secret-but-short";
        String tooLong = password.repeat(5);

        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> policy.check(tooLong));

        assertFalse(e.getMessage().contains(password));
    }

    private void assertRule(PasswordPolicyViolation expected, String password) {
        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> policy.check(password));
        assertEquals(expected, e.getRule());
    }
}
