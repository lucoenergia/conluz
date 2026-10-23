package org.lucoenergia.conluz.domain.admin.community.contact;

/**
 * A contact channel as a community admin submitted it, before {@link ContactChannelsValidator#validate} trims and checks it.
 * The type is the submitted text, so an unknown one is reported as a CTC-003 violation like any other.
 */
public record ContactChannelDraft(String type, String label, String value, String schedule, String notes) {
}
