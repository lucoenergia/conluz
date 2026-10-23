package org.lucoenergia.conluz.domain.admin.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperatorContactValidatorTest {

    /**
     * The email rule itself is the infrastructure's; here only its verdict matters, so one address is refused.
     */
    private static final ContactEmailValidator EMAIL_VALIDATOR = email -> !email.equals("refused@example.org");
    private static final OperatorContactValidator VALIDATOR = new OperatorContactValidator(EMAIL_VALIDATOR);

    @Test
    @DisplayName("CTC-005 values that are all unset are no operator contact")
    void valuesAllUnsetAreNoOperatorContact() {
        assertThat(VALIDATOR.validate(new OperatorContactDraft(null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("CTC-005 values that are all blank count as unset")
    void valuesAllBlankCountAsUnset() {
        assertThat(VALIDATOR.validate(new OperatorContactDraft("", " ", "\t", "  ", ""))).isEmpty();
    }

    @Test
    @DisplayName("CTC-004 every value is trimmed")
    void everyValueIsTrimmed() {
        assertThat(VALIDATOR.validate(new OperatorContactDraft(" Lucoenergia ", " contact@example.org ",
                " +34 600 000 000 ", " Mornings ", " Ask for support "))).contains(new OperatorContact(
                "Lucoenergia", "contact@example.org", "+34 600 000 000", "Mornings", "Ask for support"));
    }

    @Test
    @DisplayName("CTC-004 name and email only leave phone, schedule and notes null, blank ones included")
    void nameAndEmailOnlyLeaveTheOptionalValuesNull() {
        assertThat(VALIDATOR.validate(new OperatorContactDraft("Lucoenergia", "contact@example.org", " ", "", null)))
                .contains(new OperatorContact("Lucoenergia", "contact@example.org", null, null, null));
    }

    @Test
    @DisplayName("CTC-005 a blank name does not count as set")
    void aBlankNameDoesNotCountAsSet() {
        assertThat(violationsOf(new OperatorContactDraft("   ", "contact@example.org", null, null, null)))
                .containsExactly(OperatorContactViolationCode.NAME_MISSING);
    }

    @Test
    @DisplayName("CTC-005 only optional values set miss both the name and the email")
    void onlyOptionalValuesSetMissTheNameAndTheEmail() {
        assertThat(violationsOf(new OperatorContactDraft(null, null, "600000000", "Mornings", null)))
                .containsExactly(OperatorContactViolationCode.NAME_MISSING, OperatorContactViolationCode.EMAIL_MISSING);
    }

    @Test
    @DisplayName("CTC-005 every violation is reported at once")
    void everyViolationIsReportedAtOnce() {
        assertThat(violationsOf(new OperatorContactDraft(null, "refused@example.org", "600 CALL ME", null, null)))
                .containsExactly(OperatorContactViolationCode.NAME_MISSING, OperatorContactViolationCode.EMAIL_INVALID,
                        OperatorContactViolationCode.PHONE_CHARACTERS_INVALID);
    }

    @Test
    @DisplayName("CTC-005 a phone with fewer than 6 digits is a violation")
    void aPhoneWithTooFewDigitsIsAViolation() {
        assertThat(violationsOf(new OperatorContactDraft("Lucoenergia", "contact@example.org", "12345", null, null)))
                .containsExactly(OperatorContactViolationCode.PHONE_TOO_FEW_DIGITS);
    }

    @Test
    @DisplayName("CTC-005 the refusal does not repeat the configured values")
    void theRefusalDoesNotRepeatTheValues() {
        OperatorContactInvalidException exception = assertThrows(OperatorContactInvalidException.class,
                () -> VALIDATOR.validate(
                        new OperatorContactDraft("Secret Org", "refused@example.org", "600 CALL ME", null, null)));

        assertThat(exception.getMessage()).doesNotContain("Secret Org", "refused@example.org", "600 CALL ME");
    }

    private static List<OperatorContactViolationCode> violationsOf(OperatorContactDraft draft) {
        return assertThrows(OperatorContactInvalidException.class, () -> VALIDATOR.validate(draft)).getViolations();
    }
}
