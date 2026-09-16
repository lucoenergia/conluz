package org.lucoenergia.conluz.infrastructure.shared.web.reference;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A reference to a supply, embedded in responses that point at one. Carries only what a caller needs
 * to identify and display it; the full supply is fetched from its own endpoint.
 */
@Schema(requiredProperties = {"id", "code", "name"})
public class SupplyReferenceResponse {

    @Schema(description = "Internal unique identifier of the supply", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;

    @Schema(description = "Code of the supply", example = "ES0031607648137001RC0F")
    private final String code;

    @Schema(description = "Display name of the supply", example = "John Doe")
    private final String name;

    public SupplyReferenceResponse(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
