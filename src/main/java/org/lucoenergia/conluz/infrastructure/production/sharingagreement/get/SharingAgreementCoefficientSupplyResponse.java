package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public class SharingAgreementCoefficientSupplyResponse {

    @Schema(description = "Internal unique identifier of the supply", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;

    @Schema(description = "Code of the supply", example = "ES0031607648137001RC0F")
    private final String code;

    @Schema(description = "Display name of the supply", example = "John Doe")
    private final String name;

    public SharingAgreementCoefficientSupplyResponse(UUID id, String code, String name) {
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
