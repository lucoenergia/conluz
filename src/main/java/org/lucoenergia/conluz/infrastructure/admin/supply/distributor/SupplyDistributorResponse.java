package org.lucoenergia.conluz.infrastructure.admin.supply.distributor;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;

public class SupplyDistributorResponse {

    @Schema(description = "Name of the distribution company", example = "Endesa")
    private final String name;
    @Schema(description = "Code of the distribution company", example = "2")
    private final String code;
    @Schema(description = "Type of measurement point", example = "3")
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
