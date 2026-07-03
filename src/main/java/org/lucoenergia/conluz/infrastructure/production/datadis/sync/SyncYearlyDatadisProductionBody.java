package org.lucoenergia.conluz.infrastructure.production.datadis.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"year"})
public class SyncYearlyDatadisProductionBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    private String supplyCode;

    public SyncYearlyDatadisProductionBody() {
    }

    public SyncYearlyDatadisProductionBody(Integer year) {
        this.year = year;
    }

    public SyncYearlyDatadisProductionBody(Integer year, String supplyCode) {
        this.year = year;
        this.supplyCode = supplyCode;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getSupplyCode() {
        return supplyCode;
    }

    public void setSupplyCode(String supplyCode) {
        this.supplyCode = supplyCode;
    }
}
