package org.lucoenergia.conluz.infrastructure.admin.supply.distributor;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;

@Schema(requiredProperties = {"name", "code", "pointType"})
public class SupplyDistributorResponse {

    @Schema(description = "Name of the distribution company", example = "Endesa", types = {"string", "null"})
    private final String name;
    @Schema(description = "Code of the distribution company", types = {"string", "null"})
    private final String code;
    @Schema(description = "Type of measurement point", example = "3", types = {"integer", "null"})
    private final Integer pointType;

    public SupplyDistributorResponse(SupplyDistributor distributor) {
        this.name = distributor.getName();
        this.code = distributor.getCode();
        this.pointType = distributor.getPointType();
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public Integer getPointType() {
        return pointType;
    }
}
