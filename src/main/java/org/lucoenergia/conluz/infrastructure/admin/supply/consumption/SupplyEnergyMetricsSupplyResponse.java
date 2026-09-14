package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.util.UUID;

/**
 * The supply the metrics were computed for.
 */
@Schema(requiredProperties = {"id", "code", "name"})
public class SupplyEnergyMetricsSupplyResponse {

    @Schema(description = "Internal unique identifier of the supply", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;
    @Schema(description = "Code that identifies the supply", example = "ES0031300119158001DL0Y")
    private final String code;
    @Schema(description = "Name of the supply", example = "My house", types = {"string", "null"})
    private final String name;

    public SupplyEnergyMetricsSupplyResponse(Supply supply) {
        this.id = supply.getId();
        this.code = supply.getCode();
        this.name = supply.getName();
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
