package org.lucoenergia.conluz.infrastructure.admin.operator;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;

import java.util.Optional;

@Schema(requiredProperties = {"contact"})
public class GetOperatorContactResponse {

    @Schema(description = "How to reach the organisation operating this instance. Null if this instance configures "
            + "no operator contact.", types = {"object", "null"})
    private final OperatorContactResponse contact;

    private GetOperatorContactResponse(OperatorContactResponse contact) {
        this.contact = contact;
    }

    public static GetOperatorContactResponse of(Optional<OperatorContact> contact) {
        return new GetOperatorContactResponse(contact.map(OperatorContactResponse::of).orElse(null));
    }

    public OperatorContactResponse getContact() {
        return contact;
    }
}
