package org.lucoenergia.conluz.infrastructure.admin.operator;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;

@Schema(requiredProperties = {"name", "email", "phone", "schedule", "notes"})
public class OperatorContactResponse {

    @Schema(description = "The name of the organisation operating this instance.", example = "Lucoenergia")
    private final String name;

    @Schema(description = "The email address of the organisation operating this instance.",
            example = "contact@example.org")
    private final String email;

    @Schema(description = "The phone number of the organisation operating this instance. Null if it has none.",
            example = "+34 600 000 000", types = {"string", "null"})
    private final String phone;

    @Schema(description = "When the channel is attended. Null if it has no schedule.",
            example = "Monday to Friday, 9:00 to 14:00", types = {"string", "null"})
    private final String schedule;

    @Schema(description = "Free text about the channel. Null if it has none.",
            example = "Write to us if you cannot sign in.", types = {"string", "null"})
    private final String notes;

    private OperatorContactResponse(OperatorContact contact) {
        this.name = contact.name();
        this.email = contact.email();
        this.phone = contact.phone();
        this.schedule = contact.schedule();
        this.notes = contact.notes();
    }

    public static OperatorContactResponse of(OperatorContact contact) {
        return new OperatorContactResponse(contact);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getNotes() {
        return notes;
    }
}
