package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A reference to a plant, embedded in responses that point at one. Carries only what a caller needs
 * to identify and display it; the full plant is fetched from its own endpoint.
 */
@Schema(requiredProperties = {"id", "name"})
public class PlantReferenceResponse {

    @Schema(description = "Internal unique identifier of the plant", example = "a1b2c3d4-1234-5678-abcd-000000000002")
    private final UUID id;

    @Schema(description = "Display name of the plant", example = "Rooftop array")
    private final String name;

    public PlantReferenceResponse(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
