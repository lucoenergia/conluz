package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A reference to a community, embedded in responses that point at one. Carries only what a caller
 * needs to identify and display it; the full community is fetched from its own endpoint.
 */
@Schema(requiredProperties = {"id", "name"})
public class CommunityReferenceResponse {

    @Schema(description = "Internal unique identifier of the community", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private final UUID id;

    @Schema(description = "Display name of the community", example = "Riverside Energy Community")
    private final String name;

    public CommunityReferenceResponse(UUID id, String name) {
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
