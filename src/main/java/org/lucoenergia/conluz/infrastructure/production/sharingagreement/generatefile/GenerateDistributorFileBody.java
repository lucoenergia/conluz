package org.lucoenergia.conluz.infrastructure.production.sharingagreement.generatefile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"year"})
public class GenerateDistributorFileBody {

    @NotNull
    @Min(2000)
    @Max(2100)
    @Schema(description = "Used only to build the generated filename ({regulatoryCode}_{year}.txt); " +
            "the i-DE file content itself never encodes a year or period.", example = "2026")
    private Integer year;

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}
