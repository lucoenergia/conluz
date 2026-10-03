package org.lucoenergia.conluz.domain.admin.user.password;

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

    @Test
    void fourteenCodePointsAreTooShort() {
        assertRule(PasswordPolicyRule.TOO_SHORT, "a".repeat(14));
    }

    @Test
    void fifteenCodePointsAreAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.check("a".repeat(15)));
    }

    @Test
    void sixtyFourCodePointsAreAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.check("a".repeat(64)));
    }

    @Test
    void sixtyFiveCodePointsAreTooLong() {
        assertRule(PasswordPolicyRule.TOO_LONG, "a".repeat(65));
    }

    @Test
    void seventyTwoBytesAreAccepted() {
        String password = E_ACUTE.repeat(36);
        assertEquals(72, password.getBytes(StandardCharsets.UTF_8).length);

        assertDoesNotThrow(() -> PasswordPolicy.check(password));
    }

    @Test
    void seventyThreeBytesAreTooManyBytes() {
        String password = E_ACUTE.repeat(36) + "a";
        assertEquals(73, password.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(37, password.codePointCount(0, password.length()));

        assertRule(PasswordPolicyRule.TOO_MANY_BYTES, password);
    }

    @Test
    void lengthIsCountedInCodePointsNotInJavaChars() {
        // 14 code points but 28 Java chars: counting chars would wrongly accept it
        assertRule(PasswordPolicyRule.TOO_SHORT, EMOJI.repeat(14));
        // 15 code points, 60 bytes
        assertDoesNotThrow(() -> PasswordPolicy.check(EMOJI.repeat(15)));
    }

    @Test
    void charactersOutsideTheBmpCountFourBytesEach() {
        assertDoesNotThrow(() -> PasswordPolicy.check(EMOJI.repeat(18)));
        assertRule(PasswordPolicyRule.TOO_MANY_BYTES, EMOJI.repeat(19));
    }

    @Test
    void tooLongIsReportedBeforeTooManyBytes() {
        assertRule(PasswordPolicyRule.TOO_LONG, "a".repeat(73));
    }

    @Test
    void surroundingSpacesAreNotTrimmed() {
        // 13 visible characters: trimming would make it too short
        assertDoesNotThrow(() -> PasswordPolicy.check(" " + "a".repeat(13) + " "));
        assertRule(PasswordPolicyRule.TOO_SHORT, " " + "a".repeat(12) + " ");
    }

    @Test
    void onlySpacesAreAcceptedWhenLongEnough() {
        assertDoesNotThrow(() -> PasswordPolicy.check(" ".repeat(15)));
    }

    @Test
    void noCompositionRulesApply() {
        assertDoesNotThrow(() -> PasswordPolicy.check("correct horse battery staple"));
        assertDoesNotThrow(() -> PasswordPolicy.check("onlylowercaselettershere"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void absentPasswordsAreTooShort(String password) {
        assertRule(PasswordPolicyRule.TOO_SHORT, password);
    }

    @Test
    void theExceptionNeverCarriesThePassword() {
        String password = "secret-but-short";
        String tooLong = password.repeat(5);

        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> PasswordPolicy.check(tooLong));

        assertFalse(e.getMessage().contains(password));
    }

    private static void assertRule(PasswordPolicyRule expected, String password) {
        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> PasswordPolicy.check(password));
        assertEquals(expected, e.getRule());
    }
}
