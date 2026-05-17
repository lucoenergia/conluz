package org.lucoenergia.conluz.infrastructure.admin.supply.datadis;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;

public class SupplyDatadisResponse {

    @Schema(description = "Whether is an authorized third party supply or not", example = "true")
    private final Boolean thirdParty;

    public SupplyDatadisResponse(SupplyDatadis datadis) {
        this.thirdParty = datadis.isThirdParty();
    }

    public Boolean isThirdParty() {
        return thirdParty;
    }
}
