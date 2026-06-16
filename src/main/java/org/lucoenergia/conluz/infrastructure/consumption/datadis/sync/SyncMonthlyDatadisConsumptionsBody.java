package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Month;

@Schema(requiredProperties = {"year"})
public class SyncMonthlyDatadisConsumptionsBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    @Min(value = 1)
    @Max(value = 12)
    private Integer month;

    private String supplyCode;

    public SyncMonthlyDatadisConsumptionsBody() {
    }

    public SyncMonthlyDatadisConsumptionsBody(Integer year) {
        this.year = year;
    }

    public SyncMonthlyDatadisConsumptionsBody(Integer year, Integer month) {
        this.year = year;
        this.month = month;
    }

    public SyncMonthlyDatadisConsumptionsBody(Integer year, Integer month, String supplyCode) {
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

    @JsonIgnore
    public Month getMonthEnum() {
        return month != null ? Month.of(month) : null;
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
