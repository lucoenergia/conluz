package org.lucoenergia.conluz.domain.admin.community.contact;

import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.lucoenergia.conluz.domain.shared.contact.PhoneNumberFormatChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rules a community's list of contact channels follows (CTC-003), applied to what a community admin submits
 * before anything is stored. It is the one place that knows them all, including which rule an email address follows.
 */
public final class ContactChannelsValidator {

    public static final int MAX_CHANNELS = 10;
    public static final int MAX_LABEL_LENGTH = 60;
    public static final int MAX_VALUE_LENGTH = 200;
    public static final int MAX_SCHEDULE_LENGTH = 200;
    public static final int MAX_NOTES_LENGTH = 500;

    private final ContactEmailValidator emailValidity;

    public ContactChannelsValidator(ContactEmailValidator emailValidity) {
        this.emailValidity = emailValidity;
    }

    /**
     * Trims every text of every channel, turns a blank schedule or notes into {@code null}, and then checks the
     * result, so a value made only of spaces counts as blank.
     *
     * @return the channels in the submitted order
     * @throws ContactChannelsInvalidException with every violation found, if there is any
     */
    public List<ContactChannel> validate(List<ContactChannelDraft> drafts) {
        if (drafts == null) {
            throw new ContactChannelsInvalidException(
                    List.of(new ContactChannelViolation(ContactChannelViolationCode.CHANNELS_MISSING, null, Map.of())));
        }
        List<ContactChannelViolation> violations = new ArrayList<>();
        if (drafts.size() > MAX_CHANNELS) {
            violations.add(new ContactChannelViolation(ContactChannelViolationCode.CHANNELS_TOO_MANY, null,
                    Map.of("maxChannels", String.valueOf(MAX_CHANNELS))));
        }
        List<ContactChannel> channels = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            ContactChannelDraft draft = drafts.get(index);
            if (draft == null) {
                draft = new ContactChannelDraft(null, null, null, null, null);
            }
            channels.add(validate(draft, index, violations));
        }
        if (!violations.isEmpty()) {
            throw new ContactChannelsInvalidException(violations);
        }
        return List.copyOf(channels);
    }

    private ContactChannel validate(ContactChannelDraft draft, int index, List<ContactChannelViolation> violations) {
        ContactChannelType type = typeOf(trim(draft.type()));
        String label = trim(draft.label());
        String value = trim(draft.value());
        String schedule = blankToNull(trim(draft.schedule()));
        String notes = blankToNull(trim(draft.notes()));

        if (type == null) {
            violations.add(violation(ContactChannelViolationCode.TYPE_INVALID, index));
        }
        checkRequired(label, MAX_LABEL_LENGTH, ContactChannelViolationCode.LABEL_BLANK,
                ContactChannelViolationCode.LABEL_TOO_LONG, index, violations);
        boolean valueFits = checkRequired(value, MAX_VALUE_LENGTH, ContactChannelViolationCode.VALUE_BLANK,
                ContactChannelViolationCode.VALUE_TOO_LONG, index, violations);
        checkOptional(schedule, MAX_SCHEDULE_LENGTH, ContactChannelViolationCode.SCHEDULE_TOO_LONG, index, violations);
        checkOptional(notes, MAX_NOTES_LENGTH, ContactChannelViolationCode.NOTES_TOO_LONG, index, violations);
        if (valueFits && type == ContactChannelType.EMAIL && !emailValidity.isValid(value)) {
            violations.add(violation(ContactChannelViolationCode.EMAIL_INVALID, index));
        }
        if (valueFits && type == ContactChannelType.PHONE) {
            checkPhone(value, index, violations);
        }
        return new ContactChannel(type, label, value, schedule, notes);
    }

    /**
     * @return whether the text is neither blank nor too long, so its format can be checked
     */
    private static boolean checkRequired(String text, int maxLength, ContactChannelViolationCode blank,
                                         ContactChannelViolationCode tooLong, int index,
                                         List<ContactChannelViolation> violations) {
        if (text == null || text.isEmpty()) {
            violations.add(violation(blank, index));
            return false;
        }
        if (text.length() > maxLength) {
            violations.add(tooLong(tooLong, index, maxLength));
            return false;
        }
        return true;
    }

    private static void checkOptional(String text, int maxLength, ContactChannelViolationCode tooLong, int index,
                                      List<ContactChannelViolation> violations) {
        if (text != null && text.length() > maxLength) {
            violations.add(tooLong(tooLong, index, maxLength));
        }
    }

    private static void checkPhone(String value, int index, List<ContactChannelViolation> violations) {
        PhoneNumberFormatChecker.check(value).ifPresent(violation -> violations.add(switch (violation) {
            case CHARACTERS_INVALID -> violation(ContactChannelViolationCode.PHONE_CHARACTERS_INVALID, index);
            case TOO_FEW_DIGITS -> new ContactChannelViolation(ContactChannelViolationCode.PHONE_TOO_FEW_DIGITS, index,
                    Map.of("minDigits", String.valueOf(PhoneNumberFormatChecker.MIN_DIGITS)));
        }));
    }

    private static ContactChannelType typeOf(String type) {
        if (type == null) {
            return null;
        }
        for (ContactChannelType candidate : ContactChannelType.values()) {
            if (candidate.name().equals(type)) {
                return candidate;
            }
        }
        return null;
    }

    private static String trim(String text) {
        return text == null ? null : text.strip();
    }

    private static String blankToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    private static ContactChannelViolation violation(ContactChannelViolationCode code, int index) {
        return new ContactChannelViolation(code, index, Map.of());
    }

    private static ContactChannelViolation tooLong(ContactChannelViolationCode code, int index, int maxLength) {
        return new ContactChannelViolation(code, index, Map.of("maxLength", String.valueOf(maxLength)));
    }
}
