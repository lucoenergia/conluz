package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Month;

@Schema(requiredProperties = {"year"})
public class SyncMonthlyHuaweiProductionBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    @Min(value = 1)
    @Max(value = 12)
    private Integer month;

    private String plantCode;

    public SyncMonthlyHuaweiProductionBody() {
    }

    public SyncMonthlyHuaweiProductionBody(Integer year) {
        this.year = year;
    }

    public SyncMonthlyHuaweiProductionBody(Integer year, Integer month) {
        this.year = year;
        this.month = month;
    }

    public SyncMonthlyHuaweiProductionBody(Integer year, Integer month, String plantCode) {
        this.year = year;
        this.month = month;
        this.plantCode = plantCode;
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

    public String getPlantCode() {
        return plantCode;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }
}
