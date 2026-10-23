package org.lucoenergia.conluz.domain.admin.community.contact;

/**
 * Whether a value is an email address, by the rule the application already applies to the email addresses it records
 * for members, so a contact channel is not held to a second definition of one.
 */
public interface ContactEmailValidity {

    boolean isValid(String email);
}
