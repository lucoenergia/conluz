package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"year"})
public class SyncMonthlyHuaweiProductionBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    @Min(value = 1)
    @Max(value = 12)
    private Integer month;

    private String plantProviderCode;

    public SyncMonthlyHuaweiProductionBody() {
    }

    public SyncMonthlyHuaweiProductionBody(Integer year) {
        this.year = year;
    }

    public SyncMonthlyHuaweiProductionBody(Integer year, Integer month) {
        this.year = year;
        this.month = month;
    }

    public SyncMonthlyHuaweiProductionBody(Integer year, Integer month, String plantProviderCode) {
        this.year = year;
        this.month = month;
        this.plantProviderCode = plantProviderCode;
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

    public String getPlantProviderCode() {
        return plantProviderCode;
    }

    public void setPlantProviderCode(String plantProviderCode) {
        this.plantProviderCode = plantProviderCode;
    }
}
