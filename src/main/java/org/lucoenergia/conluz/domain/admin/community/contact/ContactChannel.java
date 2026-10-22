package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.Objects;

/**
 * One way of contacting a community. Only {@link ContactChannels#validate} builds one from a request, so a channel
 * obtained there always satisfies CTC-003; a channel read back from storage was validated the same way when it was
 * written.
 */
public class ContactChannel {

    private final ContactChannelType type;
    private final String label;
    private final String value;
    private final String schedule;
    private final String notes;

    /**
     * @param schedule when the channel is attended, or {@code null} if it has no schedule
     * @param notes    free text about the channel, or {@code null} if it has none
     */
    public ContactChannel(ContactChannelType type, String label, String value, String schedule, String notes) {
        this.type = type;
        this.label = label;
        this.value = value;
        this.schedule = schedule;
        this.notes = notes;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactChannel other)) return false;
        return type == other.type
                && Objects.equals(label, other.label)
                && Objects.equals(value, other.value)
                && Objects.equals(schedule, other.schedule)
                && Objects.equals(notes, other.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, label, value, schedule, notes);
    }

    @Override
    public String toString() {
        return "ContactChannel{type=" + type + ", label='" + label + "', value='" + value + "', schedule='" + schedule
                + "', notes='" + notes + "'}";
    }
}
