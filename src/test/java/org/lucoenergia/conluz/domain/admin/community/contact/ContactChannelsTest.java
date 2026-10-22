package org.lucoenergia.conluz.domain.admin.community.contact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactChannelsTest {

    /**
     * The email rule itself is the infrastructure's; here only its verdict matters, so one address is refused.
     */
    private static final ContactEmailValidity EMAIL_VALIDITY = email -> !email.equals("refused@example.org");

    @Test
    @DisplayName("CTC-003 channels are returned in the submitted order")
    void channelsKeepTheSubmittedOrder() {
        List<ContactChannel> channels = ContactChannels.validate(List.of(
                draft("PHONE", "Zeta", "600 000 000"),
                draft("ADDRESS", "Alpha", "Calle Mayor 1"),
                draft("EMAIL", "Mid", "mid@example.org")), EMAIL_VALIDITY);

        assertThat(channels).extracting(ContactChannel::getLabel).containsExactly("Zeta", "Alpha", "Mid");
        assertThat(channels).extracting(ContactChannel::getType)
                .containsExactly(ContactChannelType.PHONE, ContactChannelType.ADDRESS, ContactChannelType.EMAIL);
    }

    @Test
    @DisplayName("CTC-003 an empty list is valid")
    void anEmptyListIsValid() {
        assertThat(ContactChannels.validate(List.of(), EMAIL_VALIDITY)).isEmpty();
    }

    @Test
    @DisplayName("CTC-003 ten channels are valid and eleven are not")
    void tenChannelsAreValid_andElevenAreNot() {
        assertThat(ContactChannels.validate(Collections.nCopies(10, draft("PHONE", "Phone", "600 000 000")),
                EMAIL_VALIDITY)).hasSize(10);

        assertThat(violations(Collections.nCopies(11, draft("PHONE", "Phone", "600 000 000"))))
                .containsExactly(new ContactChannelViolation(ContactChannelViolationCode.CHANNELS_TOO_MANY, null,
                        Map.of("maxChannels", "10")));
    }

    @Test
    @DisplayName("CTC-003 a missing list is a violation")
    void aMissingListIsAViolation() {
        assertThat(violations(null)).containsExactly(
                new ContactChannelViolation(ContactChannelViolationCode.CHANNELS_MISSING, null, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAX", "phone", "Email", "aDDRESS", "", "   "})
    @DisplayName("CTC-003 a type other than PHONE, EMAIL or ADDRESS, spelt exactly, is a violation")
    void anUnknownTypeIsAViolation(String type) {
        assertThat(codesOf(draft(type, "Label", "Value"))).containsExactly(ContactChannelViolationCode.TYPE_INVALID);
    }

    @Test
    @DisplayName("CTC-003 the type is trimmed like every other text")
    void theTypeIsTrimmed() {
        assertThat(ContactChannels.validate(List.of(draft(" PHONE ", "Phone", "600 000 000")), EMAIL_VALIDITY))
                .extracting(ContactChannel::getType).containsExactly(ContactChannelType.PHONE);
    }

    @Test
    @DisplayName("CTC-003 a missing type is a violation")
    void aMissingTypeIsAViolation() {
        assertThat(codesOf(draft(null, "Label", "Value"))).containsExactly(ContactChannelViolationCode.TYPE_INVALID);
    }

    @Test
    @DisplayName("CTC-003 a missing channel breaks every required field")
    void aMissingChannelBreaksEveryRequiredField() {
        List<ContactChannelDraft> drafts = new ArrayList<>();
        drafts.add(null);

        assertThat(violations(drafts)).extracting(ContactChannelViolation::code).containsExactly(
                ContactChannelViolationCode.TYPE_INVALID, ContactChannelViolationCode.LABEL_BLANK,
                ContactChannelViolationCode.VALUE_BLANK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("CTC-003 a label blank after trimming is a violation")
    void aBlankLabelIsAViolation(String label) {
        assertThat(codesOf(draft("ADDRESS", label, "Calle Mayor 1")))
                .containsExactly(ContactChannelViolationCode.LABEL_BLANK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("CTC-003 a value blank after trimming is a violation")
    void aBlankValueIsAViolation(String value) {
        assertThat(codesOf(draft("ADDRESS", "Office", value))).containsExactly(ContactChannelViolationCode.VALUE_BLANK);
    }

    @Test
    @DisplayName("CTC-003 a null label or value is a violation")
    void aNullLabelOrValueIsAViolation() {
        assertThat(codesOf(draft("ADDRESS", null, null))).containsExactly(ContactChannelViolationCode.LABEL_BLANK,
                ContactChannelViolationCode.VALUE_BLANK);
    }

    @Test
    @DisplayName("CTC-003 each text is valid at its length limit, counted after trimming")
    void eachTextIsValidAtItsLimit() {
        List<ContactChannel> channels = ContactChannels.validate(List.of(new ContactChannelDraft("ADDRESS",
                " " + "a".repeat(60) + " ", "b".repeat(200), "c".repeat(200), "d".repeat(500))), EMAIL_VALIDITY);

        assertThat(channels).containsExactly(new ContactChannel(ContactChannelType.ADDRESS, "a".repeat(60),
                "b".repeat(200), "c".repeat(200), "d".repeat(500)));
    }

    @Test
    @DisplayName("CTC-003 each text one character over its length limit is a violation carrying the limit")
    void eachTextOverItsLimitIsAViolation() {
        assertThat(violations(List.of(new ContactChannelDraft("ADDRESS", "a".repeat(61), "b".repeat(201),
                "c".repeat(201), "d".repeat(501))))).containsExactly(
                new ContactChannelViolation(ContactChannelViolationCode.LABEL_TOO_LONG, 0, Map.of("maxLength", "60")),
                new ContactChannelViolation(ContactChannelViolationCode.VALUE_TOO_LONG, 0, Map.of("maxLength", "200")),
                new ContactChannelViolation(ContactChannelViolationCode.SCHEDULE_TOO_LONG, 0,
                        Map.of("maxLength", "200")),
                new ContactChannelViolation(ContactChannelViolationCode.NOTES_TOO_LONG, 0, Map.of("maxLength", "500")));
    }

    @Test
    @DisplayName("CTC-003 every text is trimmed, and a blank schedule or notes becomes null")
    void textsAreTrimmed_andBlankScheduleAndNotesBecomeNull() {
        List<ContactChannel> channels = ContactChannels.validate(List.of(
                new ContactChannelDraft("EMAIL", "  General ", " info@example.org  ", "  ", ""),
                new ContactChannelDraft("PHONE", "Treasurer", "600 000 000", " Weekdays ", " Urgent only. ")),
                EMAIL_VALIDITY);

        assertThat(channels).containsExactly(
                new ContactChannel(ContactChannelType.EMAIL, "General", "info@example.org", null, null),
                new ContactChannel(ContactChannelType.PHONE, "Treasurer", "600 000 000", "Weekdays", "Urgent only."));
    }

    @Test
    @DisplayName("CTC-003 an EMAIL value the email rule refuses is a violation, and the rule sees it trimmed")
    void anInvalidEmailIsAViolation() {
        assertThat(codesOf(draft("EMAIL", "General", " refused@example.org ")))
                .containsExactly(ContactChannelViolationCode.EMAIL_INVALID);
        assertThat(codesOf(draft("EMAIL", "General", "accepted@example.org"))).isEmpty();
    }

    @Test
    @DisplayName("CTC-003 the email rule applies to EMAIL channels only")
    void theEmailRuleAppliesToEmailChannelsOnly() {
        assertThat(codesOf(draft("ADDRESS", "Office", "refused@example.org"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"600000", "+34 600 000 000", "976 00 00 00", "+346000000"})
    @DisplayName("CTC-003 a phone number of digits, spaces and a leading +, with at least 6 digits, is valid")
    void aValidPhoneNumberIsValid(String phone) {
        assertThat(codesOf(draft("PHONE", "Phone", phone))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"600 CALL ME", "600-000-000", "(976) 000 000", "34+600000000", "++34600000000",
            "600.000.000"})
    @DisplayName("CTC-003 a phone number with anything but digits, spaces and a leading + is a violation")
    void aPhoneNumberWithOtherCharactersIsAViolation(String phone) {
        assertThat(codesOf(draft("PHONE", "Phone", phone)))
                .containsExactly(ContactChannelViolationCode.PHONE_CHARACTERS_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"60000", "+34 12 3", "+ 1 2 3 4 5", "+"})
    @DisplayName("CTC-003 a phone number with fewer than 6 digits is a violation carrying the minimum")
    void aPhoneNumberWithTooFewDigitsIsAViolation(String phone) {
        assertThat(violations(List.of(draft("PHONE", "Phone", phone)))).containsExactly(
                new ContactChannelViolation(ContactChannelViolationCode.PHONE_TOO_FEW_DIGITS, 0,
                        Map.of("minDigits", "6")));
    }

    @Test
    @DisplayName("CTC-003 the phone rule applies to PHONE channels only")
    void thePhoneRuleAppliesToPhoneChannelsOnly() {
        assertThat(codesOf(draft("ADDRESS", "Office", "Calle Mayor 1"))).isEmpty();
    }

    @Test
    @DisplayName("CTC-003 the format of a value that is too long is not checked")
    void theFormatOfATooLongValueIsNotChecked() {
        assertThat(codesOf(draft("PHONE", "Phone", "x".repeat(201))))
                .containsExactly(ContactChannelViolationCode.VALUE_TOO_LONG);
    }

    @Test
    @DisplayName("CTC-003 every violation of every channel is reported, each with its channel's index")
    void everyViolationIsReported() {
        assertThat(violations(List.of(
                draft("PHONE", "Treasurer", "600 000 000"),
                draft("FAX", "", "976"),
                draft("EMAIL", "General", "refused@example.org")))).containsExactly(
                new ContactChannelViolation(ContactChannelViolationCode.TYPE_INVALID, 1, Map.of()),
                new ContactChannelViolation(ContactChannelViolationCode.LABEL_BLANK, 1, Map.of()),
                new ContactChannelViolation(ContactChannelViolationCode.EMAIL_INVALID, 2, Map.of()));
    }

    private static ContactChannelDraft draft(String type, String label, String value) {
        return new ContactChannelDraft(type, label, value, null, null);
    }

    private static List<ContactChannelViolationCode> codesOf(ContactChannelDraft draft) {
        return violations(List.of(draft)).stream().map(ContactChannelViolation::code).toList();
    }

    private static List<ContactChannelViolation> violations(List<ContactChannelDraft> drafts) {
        try {
            ContactChannels.validate(drafts, EMAIL_VALIDITY);
            return List.of();
        } catch (ContactChannelsInvalidException e) {
            return e.getViolations();
        }
    }

    @Test
    void theExceptionCarriesTheViolations() {
        ContactChannelsInvalidException e = assertThrows(ContactChannelsInvalidException.class,
                () -> ContactChannels.validate(List.of(draft("PHONE", "", "600 000 000")), EMAIL_VALIDITY));

        assertThat(e.getViolations()).containsExactly(
                new ContactChannelViolation(ContactChannelViolationCode.LABEL_BLANK, 0, Map.of()));
    }
}
