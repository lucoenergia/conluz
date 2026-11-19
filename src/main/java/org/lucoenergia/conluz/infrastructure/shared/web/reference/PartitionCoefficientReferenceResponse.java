package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A reference to a partition coefficient period, embedded in responses that point at one; the period itself is read
 * through its agreement's or its supply's coefficients.
 */
@Schema(requiredProperties = {"id"})
public class PartitionCoefficientReferenceResponse {

    @Schema(description = "Internal unique identifier of the coefficient period",
            example = "6f1f3c2e-1234-5678-abcd-000000000001")
    private final UUID id;

    public PartitionCoefficientReferenceResponse(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
