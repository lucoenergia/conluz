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

    private String plantCode;
    private java.util.UUID communityId;

    public SyncYearlyHuaweiProductionBody() {
    }

    public SyncYearlyHuaweiProductionBody(Integer year) {
        this.year = year;
    }

    public SyncYearlyHuaweiProductionBody(Integer year, String plantCode) {
        this.year = year;
        this.plantCode = plantCode;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public java.util.UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(java.util.UUID communityId) {
        this.communityId = communityId;
    }
}
