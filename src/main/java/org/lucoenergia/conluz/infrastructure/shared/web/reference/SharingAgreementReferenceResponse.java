package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.util.UUID;

/**
 * A reference to a sharing agreement, embedded in responses that point at one. Carries only what a
 * caller needs to identify and display it; the full agreement is fetched from its own endpoint.
 *
 * <p>{@code status} is included because it changes how a caller may present the agreement -- a DRAFT
 * is still editable, a PUBLISHED one is in force and a SUPERSEDED one is historical -- and resolving
 * it would otherwise cost a request per referenced agreement.
 */
@Schema(requiredProperties = {"id", "name", "status"})
public class SharingAgreementReferenceResponse {

    @Schema(description = "Internal unique identifier of the agreement", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID id;

    @Schema(description = "Human-readable label for the agreement", example = "Winter 2024 distribution")
    private final String name;

    @Schema(description = "Status of the agreement: DRAFT, PUBLISHED or SUPERSEDED", example = "PUBLISHED")
    private final SharingAgreementStatus status;

    public SharingAgreementReferenceResponse(UUID id, String name, SharingAgreementStatus status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SharingAgreementStatus getStatus() {
        return status;
    }
}
