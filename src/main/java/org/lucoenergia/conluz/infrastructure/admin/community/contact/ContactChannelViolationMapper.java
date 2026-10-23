package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelViolation;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelViolationCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a domain {@link ContactChannelViolation} to the typed {@link RestErrorDetail} contract. An explicit
 * {@link EnumMap} pairing, as in {@code DistributorFileErrorMapper}, so a drift between
 * {@link ContactChannelViolationCode} and {@link RestErrorCode} fails at startup instead of answering a code-less
 * error.
 *
 * <p>The params carry {@code index}, the 0-based position of the offending channel in the submitted list, when the
 * violation is about one channel, plus the limit that was broken. The message numbers channels from 1.</p>
 */
@Component
public class ContactChannelViolationMapper {

    private static final Map<ContactChannelViolationCode, RestErrorCode> CODES =
            new EnumMap<>(ContactChannelViolationCode.class);
    private static final Map<ContactChannelViolationCode, String> MESSAGE_KEYS =
            new EnumMap<>(ContactChannelViolationCode.class);
    private static final Map<ContactChannelViolationCode, String> LIMIT_KEYS =
            new EnumMap<>(ContactChannelViolationCode.class);

    static {
        register(ContactChannelViolationCode.CHANNELS_MISSING, RestErrorCode.CONTACT_CHANNELS_MISSING,
                "error.contact.channels.missing", null);
        register(ContactChannelViolationCode.CHANNELS_TOO_MANY, RestErrorCode.CONTACT_CHANNELS_TOO_MANY,
                "error.contact.channels.too.many", "maxChannels");
        register(ContactChannelViolationCode.TYPE_INVALID, RestErrorCode.CONTACT_CHANNEL_TYPE_INVALID,
                "error.contact.channel.type.invalid", null);
        register(ContactChannelViolationCode.LABEL_BLANK, RestErrorCode.CONTACT_CHANNEL_LABEL_BLANK,
                "error.contact.channel.label.blank", null);
        register(ContactChannelViolationCode.LABEL_TOO_LONG, RestErrorCode.CONTACT_CHANNEL_LABEL_TOO_LONG,
                "error.contact.channel.label.too.long", "maxLength");
        register(ContactChannelViolationCode.VALUE_BLANK, RestErrorCode.CONTACT_CHANNEL_VALUE_BLANK,
                "error.contact.channel.value.blank", null);
        register(ContactChannelViolationCode.VALUE_TOO_LONG, RestErrorCode.CONTACT_CHANNEL_VALUE_TOO_LONG,
                "error.contact.channel.value.too.long", "maxLength");
        register(ContactChannelViolationCode.SCHEDULE_TOO_LONG, RestErrorCode.CONTACT_CHANNEL_SCHEDULE_TOO_LONG,
                "error.contact.channel.schedule.too.long", "maxLength");
        register(ContactChannelViolationCode.NOTES_TOO_LONG, RestErrorCode.CONTACT_CHANNEL_NOTES_TOO_LONG,
                "error.contact.channel.notes.too.long", "maxLength");
        register(ContactChannelViolationCode.EMAIL_INVALID, RestErrorCode.CONTACT_CHANNEL_EMAIL_INVALID,
                "error.contact.channel.email.invalid", null);
        register(ContactChannelViolationCode.PHONE_CHARACTERS_INVALID,
                RestErrorCode.CONTACT_CHANNEL_PHONE_CHARACTERS_INVALID,
                "error.contact.channel.phone.characters.invalid", null);
        register(ContactChannelViolationCode.PHONE_TOO_FEW_DIGITS, RestErrorCode.CONTACT_CHANNEL_PHONE_TOO_FEW_DIGITS,
                "error.contact.channel.phone.too.few.digits", "minDigits");
        for (ContactChannelViolationCode code : ContactChannelViolationCode.values()) {
            if (!CODES.containsKey(code)) {
                throw new IllegalStateException("No error code is registered for " + code);
            }
        }
    }

    private static void register(ContactChannelViolationCode code, RestErrorCode restErrorCode, String messageKey,
                                 String limitKey) {
        CODES.put(code, restErrorCode);
        MESSAGE_KEYS.put(code, messageKey);
        if (limitKey != null) {
            LIMIT_KEYS.put(code, limitKey);
        }
    }

    private final MessageSource messageSource;

    public ContactChannelViolationMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public RestErrorDetail toRestErrorDetail(ContactChannelViolation violation, Locale locale) {
        ContactChannelViolationCode code = violation.code();
        String limitKey = LIMIT_KEYS.get(code);
        String limit = limitKey != null ? violation.params().get(limitKey) : null;

        List<Object> args = new ArrayList<>();
        Map<String, String> params = new HashMap<>(violation.params());
        if (violation.index() != null) {
            args.add(violation.index() + 1);
            params.put("index", String.valueOf(violation.index()));
        }
        if (limit != null) {
            args.add(limit);
        }
        String message = messageSource.getMessage(MESSAGE_KEYS.get(code), args.toArray(), locale);
        return new RestErrorDetail(message, CODES.get(code), params.isEmpty() ? null : Map.copyOf(params));
    }
}
