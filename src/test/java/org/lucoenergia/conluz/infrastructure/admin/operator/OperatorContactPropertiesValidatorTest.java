package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperatorContactPropertiesValidatorTest {

    /**
     * The email rule itself is the infrastructure's; here only its verdict matters, so one address is refused.
     */
    private static final ContactEmailValidator EMAIL_VALIDITY = email -> !email.equals("refused@example.org");
    private static final OperatorContactPropertiesValidator VALIDATOR =
            new OperatorContactPropertiesValidator(EMAIL_VALIDITY);

    @Test
    @DisplayName("CTC-005 settings that are all unset are no operator contact")
    void settingsAllUnsetAreNoOperatorContact() {
        assertThat(VALIDATOR.validate(new OperatorContactProperties(null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("CTC-005 settings that are all blank count as unset")
    void settingsAllBlankCountAsUnset() {
        assertThat(VALIDATOR.validate(new OperatorContactProperties("", " ", "\t", "  ", ""))).isEmpty();
    }

    @Test
    @DisplayName("CTC-004 every value is trimmed")
    void everyValueIsTrimmed() {
        assertThat(VALIDATOR.validate(new OperatorContactProperties(" Lucoenergia ", " contact@example.org ",
                " +34 600 000 000 ", " Mornings ", " Ask for support "))).contains(new OperatorContact(
                "Lucoenergia", "contact@example.org", "+34 600 000 000", "Mornings", "Ask for support"));
    }

    @Test
    @DisplayName("CTC-004 name and email only leave phone, schedule and notes null, blank ones included")
    void nameAndEmailOnlyLeaveTheOptionalValuesNull() {
        assertThat(VALIDATOR.validate(new OperatorContactProperties("Lucoenergia", "contact@example.org", " ", "",
                null))).contains(new OperatorContact("Lucoenergia", "contact@example.org", null, null, null));
    }

    @Test
    @DisplayName("CTC-005 a blank name does not count as set")
    void aBlankNameDoesNotCountAsSet() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> VALIDATOR.validate(
                new OperatorContactProperties("   ", "contact@example.org", null, null, null)));

        assertThat(exception.getMessage()).contains("CONLUZ_OPERATOR_CONTACT_NAME is not set");
    }

    @Test
    @DisplayName("CTC-005 every problem is reported at once, by variable")
    void everyProblemIsReportedAtOnce() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> VALIDATOR.validate(
                new OperatorContactProperties(null, "refused@example.org", "600 CALL ME", null, null)));

        assertThat(exception.getMessage()).contains(
                "CONLUZ_OPERATOR_CONTACT_NAME is not set",
                "CONLUZ_OPERATOR_CONTACT_EMAIL is not an email address",
                "CONLUZ_OPERATOR_CONTACT_PHONE may only have digits, spaces and a leading +");
    }

    @Test
    @DisplayName("CTC-005 a phone with fewer than 6 digits is reported with the minimum")
    void aPhoneWithTooFewDigitsIsReportedWithTheMinimum() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> VALIDATOR.validate(
                new OperatorContactProperties("Lucoenergia", "contact@example.org", "12345", null, null)));

        assertThat(exception.getMessage()).contains("CONLUZ_OPERATOR_CONTACT_PHONE must have at least 6 digits");
    }

    @Test
    @DisplayName("CTC-005 the refusal does not repeat the configured values")
    void theRefusalDoesNotRepeatTheValues() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> VALIDATOR.validate(
                new OperatorContactProperties("Secret Org", "refused@example.org", "600 CALL ME", null, null)));

        assertThat(exception.getMessage()).doesNotContain("Secret Org", "refused@example.org", "600 CALL ME");
    }
}
