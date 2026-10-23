package org.lucoenergia.conluz.domain.shared.contact;

/**
 * Whether a value is an email address, by the rule the application already applies to the email addresses it records
 * for members, so no contact is held to a second definition of one.
 */
public interface ContactEmailValidity {

    boolean isValid(String email);
}
