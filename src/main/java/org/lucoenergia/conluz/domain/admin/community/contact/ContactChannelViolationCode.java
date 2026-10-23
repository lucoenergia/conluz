package org.lucoenergia.conluz.domain.admin.community.contact;

/**
 * Each way a submitted list of contact channels can break CTC-003.
 */
public enum ContactChannelViolationCode {
    /** The list itself is missing. */
    CHANNELS_MISSING,
    /** The list has more than {@link ContactChannelsValidator#MAX_CHANNELS} channels. */
    CHANNELS_TOO_MANY,
    /** A channel is missing, or has no type or one that is not a {@link ContactChannelType}. */
    TYPE_INVALID,
    LABEL_BLANK,
    LABEL_TOO_LONG,
    VALUE_BLANK,
    VALUE_TOO_LONG,
    SCHEDULE_TOO_LONG,
    NOTES_TOO_LONG,
    /** The value of an {@link ContactChannelType#EMAIL} channel is not an email address. */
    EMAIL_INVALID,
    /** The value of a {@link ContactChannelType#PHONE} channel has something other than digits, spaces and one leading {@code +}. */
    PHONE_CHARACTERS_INVALID,
    /**
     * The value of a {@link ContactChannelType#PHONE} channel has fewer than
     * {@link ContactChannelsValidator#MIN_PHONE_DIGITS} digits.
     */
    PHONE_TOO_FEW_DIGITS
}
