package org.lucoenergia.conluz.infrastructure.admin.supply.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;

import java.time.LocalDate;

public class SupplyContractResponse {

    @Schema(description = "Date on which the supply point was registered as valid", example = "2024-01-15")
    private final LocalDate validDateFrom;

    public SupplyContractResponse(SupplyContract contract) {
        this.validDateFrom = contract.getValidDateFrom();
    }

    public LocalDate getValidDateFrom() {
        return validDateFrom;
    }
}
