package org.lucoenergia.conluz.infrastructure.datadis.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Schema(requiredProperties = {"year"})
public class SyncDatadisConsumptionsBody {

    @NotNull
    @Min(value = 2000)
    @Max(value = 2100)
    private Integer year;

    private String supplyCode;

    public SyncDatadisConsumptionsBody() {
    }

    public SyncDatadisConsumptionsBody(Integer year) {
        this.year = year;
    }

    public SyncDatadisConsumptionsBody(Integer year, String supplyCode) {
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

    /**
     * Converts the year to the start date (January 1st of the year).
     *
     * @return LocalDate representing January 1st of the specified year
     */
    @JsonIgnore
    public LocalDate getStartDate() {
        return LocalDate.of(year, 1, 1);
    }

    /**
     * Converts the year to the end date (December 31st of the year), or today if that is earlier.
     *
     * @param clock the source of today, read in the JVM's default zone
     * @return LocalDate representing December 31st of the specified year, or today
     */
    @JsonIgnore
    public LocalDate getEndDate(Clock clock) {
        LocalDate lastDayOfYear = LocalDate.of(year, 12, 31);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        if (lastDayOfYear.isAfter(today)) {
            return today;
        }
        return lastDayOfYear;
    }
}
