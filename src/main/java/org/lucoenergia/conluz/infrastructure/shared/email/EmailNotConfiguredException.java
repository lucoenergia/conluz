package org.lucoenergia.conluz.infrastructure.shared.email;

/**
 * Sending is switched on, but a required setting is missing or invalid.
 */
class EmailNotConfiguredException extends Exception {

    EmailNotConfiguredException() {
        super("Email sending is enabled but not configured");
    }
}
