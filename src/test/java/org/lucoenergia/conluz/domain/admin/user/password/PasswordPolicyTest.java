package org.lucoenergia.conluz.domain.admin.user.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    /** U+1F600, outside the Basic Multilingual Plane: one code point, two Java chars, four UTF-8 bytes. */
    private static final String EMOJI = "😀";
    /** U+00E9: one code point, one Java char, two UTF-8 bytes. */
    private static final String E_ACUTE = "é";

    private static final String LISTED = "correct-horse-listed";
    private static final String OTHER_LISTED = "another listed one";

    private final PasswordPolicy policy = new PasswordPolicy(password -> false);

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

    @Test
    @DisplayName("ACC-022 a listed password is compromised")
    void aListedPasswordIsCompromised() {
        PasswordPolicy listing = new PasswordPolicy(new RecordingBlocklist(LISTED, OTHER_LISTED));

        assertRule(listing, PasswordPolicyViolation.COMPROMISED, LISTED);
        assertRule(listing, PasswordPolicyViolation.COMPROMISED, OTHER_LISTED);
    }

    @Test
    @DisplayName("ACC-022 a listed password is compromised whatever its case")
    void aListedPasswordInAnotherCaseIsCompromised() {
        PasswordPolicy listing = new PasswordPolicy(new RecordingBlocklist(LISTED, OTHER_LISTED));

        assertRule(listing, PasswordPolicyViolation.COMPROMISED, "Correct-Horse-Listed");
        assertRule(listing, PasswordPolicyViolation.COMPROMISED, "CORRECT-HORSE-LISTED");
    }

    @Test
    @DisplayName("ACC-022 a password that is not listed is accepted")
    void anUnlistedPasswordIsAccepted() {
        PasswordPolicy listing = new PasswordPolicy(new RecordingBlocklist(LISTED, OTHER_LISTED));

        assertDoesNotThrow(() -> listing.check("correct-horse-unlisted"));
    }

    @Test
    @DisplayName("ACC-022 a listed password of 9 code points is too short, not compromised")
    void aListedPasswordOf9CodePointsIsTooShort() {
        String nine = "listed-09";
        RecordingBlocklist blocklist = new RecordingBlocklist(nine, LISTED);

        assertRule(new PasswordPolicy(blocklist), PasswordPolicyViolation.TOO_SHORT, nine);
        assertTrue(blocklist.received.isEmpty());
    }

    @Test
    @DisplayName("ACC-022 a password of 65 code points is too long, and the list is not consulted")
    void aPasswordOf65CodePointsIsTooLongWithoutConsultingTheList() {
        String tooLong = "a".repeat(65);
        RecordingBlocklist blocklist = new RecordingBlocklist(tooLong, LISTED);

        assertRule(new PasswordPolicy(blocklist), PasswordPolicyViolation.TOO_LONG, tooLong);
        assertTrue(blocklist.received.isEmpty());
    }

    @Test
    @DisplayName("ACC-022 a password over 72 bytes is too many bytes, and the list is not consulted")
    void aPasswordOverTheByteLimitIsTooManyBytesWithoutConsultingTheList() {
        String tooManyBytes = E_ACUTE.repeat(37);
        RecordingBlocklist blocklist = new RecordingBlocklist(tooManyBytes, LISTED);

        assertRule(new PasswordPolicy(blocklist), PasswordPolicyViolation.TOO_MANY_BYTES, tooManyBytes);
        assertTrue(blocklist.received.isEmpty());
    }

    @Test
    @DisplayName("ACC-022 the list is asked about the candidate lowercased with the root locale")
    void theListReceivesTheLowercasedCandidate() {
        RecordingBlocklist blocklist = new RecordingBlocklist(LISTED, OTHER_LISTED);
        PasswordPolicy listing = new PasswordPolicy(blocklist);

        // Under the Turkish default locale, lowercasing with that locale would turn "I" into a dotless "ı"
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertDoesNotThrow(() -> listing.check("Mixed CASE Password WITH I"));
        } finally {
            Locale.setDefault(defaultLocale);
        }

        assertEquals(List.of("mixed case password with i"), blocklist.received);
    }

    /** A list holding fixed entries, which records every candidate it is asked about. */
    private static final class RecordingBlocklist implements PasswordBlocklist {

        private final Set<String> entries;
        private final List<String> received = new ArrayList<>();

        private RecordingBlocklist(String... entries) {
            this.entries = Set.of(entries);
        }

        @Override
        public boolean contains(String lowercasedPassword) {
            received.add(lowercasedPassword);
            return entries.contains(lowercasedPassword);
        }
    }

    private void assertRule(PasswordPolicyViolation expected, String password) {
        assertRule(policy, expected, password);
    }

    private static void assertRule(PasswordPolicy policy, PasswordPolicyViolation expected, String password) {
        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> policy.check(password));
        assertEquals(expected, e.getRule());
    }
}
