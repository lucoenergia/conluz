package org.lucoenergia.conluz.infrastructure.production.plant;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(requiredProperties = {"id"})
public class PlantCommunityResponse {

    @Schema(description = "Internal unique identifier of the community", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID id;

    public PlantCommunityResponse(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
