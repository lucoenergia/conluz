package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelType;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelsValidator;

/**
 * Carries no validation constraint on purpose: every rule is checked by {@link ContactChannelsValidator}, which
 * answers each broken rule with its own error code, where a constraint here would answer a 400 without one. For the
 * same reason {@code type} is read as text: an unknown type is a rule broken like any other, not a body that cannot
 * be read.
 */
@Schema(requiredProperties = {"type", "label", "value", "schedule", "notes"})
public class ContactChannelBody {

    @Schema(description = "The kind of channel. The value must be a phone number for PHONE, an email address for "
            + "EMAIL, and a postal address for ADDRESS.", implementation = ContactChannelType.class)
    private String type;

    @Schema(description = "The name shown for the channel. Not blank, at most 60 characters.", example = "Office",
            maxLength = 60)
    private String label;

    @Schema(description = "The phone number, email address or postal address. Not blank, at most 200 characters. A "
            + "phone number may contain only digits, spaces and a leading +, and has at least 6 digits.",
            example = "+34 600 000 000", maxLength = 200)
    private String value;

    @Schema(description = "When the channel is attended, as free text of at most 200 characters. Null, an empty or a "
            + "blank string if it has none.", example = "Monday to Friday, 9:00 to 14:00", maxLength = 200,
            types = {"string", "null"})
    private String schedule;

    @Schema(description = "Free text about the channel, of at most 500 characters. Null, an empty or a blank string "
            + "if it has none.", example = "Ask for the treasurer.", maxLength = 500, types = {"string", "null"})
    private String notes;

    public ContactChannelBody() {
    }

    public ContactChannelBody(String type, String label, String value, String schedule, String notes) {
        this.type = type;
        this.label = label;
        this.value = value;
        this.schedule = schedule;
        this.notes = notes;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ContactChannelDraft toDraft() {
        return new ContactChannelDraft(type, label, value, schedule, notes);
    }
}
