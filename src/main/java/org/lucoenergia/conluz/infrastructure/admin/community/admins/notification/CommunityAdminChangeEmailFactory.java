package org.lucoenergia.conluz.infrastructure.admin.community.admins.notification;

import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;

/**
 * The email telling a community's admins about a change to its admins (#466): plain text, in Spanish. It names the
 * affected person and the actor by the full names of their member records, and never carries a DNI, a contact email
 * or a phone (MEM-036).
 */
@Component
public class CommunityAdminChangeEmailFactory {

    public static final EmailCategory CATEGORY = new EmailCategory("COMMUNITY_ADMIN_CHANGE");
    static final Locale LOCALE = Locale.forLanguageTag("es");
    private static final String PREFIX = "email.community-admin-change.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** The changes with their own sentence when the affected person made them. */
    private static final Set<CommunityAdminChange> SELF_SENTENCES = Set.of(CommunityAdminChange.DEMOTED,
            CommunityAdminChange.REMOVED);

    private final MessageSource messageSource;
    private final TimeConfiguration timeConfiguration;

    public CommunityAdminChangeEmailFactory(MessageSource messageSource, TimeConfiguration timeConfiguration) {
        this.messageSource = messageSource;
        this.timeConfiguration = timeConfiguration;
    }

    /**
     * What one change says, whoever it is sent to.
     *
     * @param actorName  the actor's full name, or null when the change was the platform operator's
     * @param byAffected true when the affected person made the change themselves
     */
    public record Message(CommunityAdminChange change, String communityName, String affectedName, String actorName,
                          boolean byAffected, Instant at) {
    }

    /**
     * @param toAffected true for the email to the affected person, written to them; false for the community's other
     *                   admins
     */
    public Email build(String recipient, Message message, boolean toAffected) {
        String actor = message.actorName() != null
                ? message.actorName()
                : text("operator");
        String sentenceKey = (toAffected ? "affected." : "others.") + message.change().name()
                + (!toAffected && message.byAffected() && SELF_SENTENCES.contains(message.change()) ? ".self" : "");
        String sentence = text(sentenceKey, message.communityName(), message.affectedName(), actor);

        ZoneId zone = timeConfiguration.getZoneId();
        ZonedDateTime at = message.at().atZone(zone);
        String subject = text("subject", message.communityName());
        String body = text(toAffected ? "body.affected" : "body.others", sentence, DATE.format(at), TIME.format(at),
                zone.getDisplayName(TextStyle.FULL, LOCALE), message.communityName());
        return new Email(recipient, subject, body, CATEGORY);
    }

    private String text(String key, Object... arguments) {
        return messageSource.getMessage(PREFIX + key, arguments, LOCALE);
    }
}
