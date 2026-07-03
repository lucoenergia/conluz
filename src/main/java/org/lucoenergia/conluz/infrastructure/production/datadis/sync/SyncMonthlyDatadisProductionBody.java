package org.lucoenergia.conluz.infrastructure.production.datadis.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"year"})
public class SyncMonthlyDatadisProductionBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    @Min(value = 1)
    @Max(value = 12)
    private Integer month;

    private String supplyCode;

    public SyncMonthlyDatadisProductionBody() {
    }

    public SyncMonthlyDatadisProductionBody(Integer year) {
        this.year = year;
    }

    public SyncMonthlyDatadisProductionBody(Integer year, Integer month) {
        this.year = year;
        this.month = month;
    }

    public SyncMonthlyDatadisProductionBody(Integer year, Integer month, String supplyCode) {
        this.year = year;
        this.month = month;
        this.supplyCode = supplyCode;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public String getSupplyCode() {
        return supplyCode;
    }

    public void setSupplyCode(String supplyCode) {
        this.supplyCode = supplyCode;
    }
}
