package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"year"})
public class SyncYearlyHuaweiProductionBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    private String plantProviderCode;

    public SyncYearlyHuaweiProductionBody() {
    }

    public SyncYearlyHuaweiProductionBody(Integer year) {
        this.year = year;
    }

    public SyncYearlyHuaweiProductionBody(Integer year, String plantProviderCode) {
        this.year = year;
        this.plantProviderCode = plantProviderCode;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPlantProviderCode() {
        return plantProviderCode;
    }

    public void setPlantProviderCode(String plantProviderCode) {
        this.plantProviderCode = plantProviderCode;
    }
}
