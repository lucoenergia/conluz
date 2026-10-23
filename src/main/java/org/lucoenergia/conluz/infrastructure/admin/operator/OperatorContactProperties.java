package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the operator contact settings, each one from the environment variable named by the constants below.
 *
 * <p>Deliberately not validated at binding time: the domain's
 * {@link org.lucoenergia.conluz.domain.admin.operator.OperatorContactValidator} checks the settings as a whole when
 * {@link GetOperatorContactServiceImpl} is built, so a partial or invalid configuration stops the application from
 * starting (CTC-005) with a message naming the environment variables to fix, which is what an operator sets, rather
 * than the property paths.</p>
 *
 * @param name     the organisation's name; empty when not configured
 * @param email    the organisation's email address; empty when not configured
 * @param phone    the organisation's phone number; empty when not configured
 * @param schedule when the organisation attends; empty when not configured
 * @param notes    free text about the contact; empty when not configured
 */
@ConfigurationProperties(prefix = "conluz.operator-contact")
public record OperatorContactProperties(String name, String email, String phone, String schedule, String notes) {

    public static final String NAME_VARIABLE = "CONLUZ_OPERATOR_CONTACT_NAME";
    public static final String EMAIL_VARIABLE = "CONLUZ_OPERATOR_CONTACT_EMAIL";
    public static final String PHONE_VARIABLE = "CONLUZ_OPERATOR_CONTACT_PHONE";
    public static final String SCHEDULE_VARIABLE = "CONLUZ_OPERATOR_CONTACT_SCHEDULE";
    public static final String NOTES_VARIABLE = "CONLUZ_OPERATOR_CONTACT_NOTES";
}
