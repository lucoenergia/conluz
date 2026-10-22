package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelType;

@Schema(requiredProperties = {"type", "label", "value", "schedule", "notes"})
public class ContactChannelResponse {

    @Schema(description = "The kind of channel.")
    private final ContactChannelType type;

    @Schema(description = "The name shown for the channel.", example = "Office")
    private final String label;

    @Schema(description = "The phone number, email address or postal address.", example = "+34 600 000 000")
    private final String value;

    @Schema(description = "When the channel is attended. Null if it has no schedule.",
            example = "Monday to Friday, 9:00 to 14:00", types = {"string", "null"})
    private final String schedule;

    @Schema(description = "Free text about the channel. Null if it has none.", example = "Ask for the treasurer.",
            types = {"string", "null"})
    private final String notes;

    private ContactChannelResponse(ContactChannel channel) {
        this.type = channel.getType();
        this.label = channel.getLabel();
        this.value = channel.getValue();
        this.schedule = channel.getSchedule();
        this.notes = channel.getNotes();
    }

    public static ContactChannelResponse of(ContactChannel channel) {
        return new ContactChannelResponse(channel);
    }

    public ContactChannelType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getNotes() {
        return notes;
    }
}
