package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A reference to a user, embedded in responses that point at one. Carries only the id: nothing else of the account
 * is the caller's to read.
 */
@Schema(requiredProperties = {"id"})
public class UserReferenceResponse {

    @Schema(description = "Internal unique identifier of the user", example = "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1")
    private final UUID id;

    public UserReferenceResponse(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
