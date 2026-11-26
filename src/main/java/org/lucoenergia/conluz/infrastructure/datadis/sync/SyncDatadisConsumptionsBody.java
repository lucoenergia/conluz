package org.lucoenergia.conluz.infrastructure.datadis.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindows;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

@Schema(requiredProperties = {"monthsBack"})
public class SyncDatadisConsumptionsBody {

    @Schema(description = "How many months before the current one the synchronization starts: 0 synchronizes the "
            + "current month only.", example = "3")
    @NotNull
    @Min(value = 0)
    @Max(value = DatadisSyncWindows.MAX_LOOKBACK_MONTHS)
    private Integer monthsBack;

    private String supplyCode;

    public SyncDatadisConsumptionsBody() {
    }

    public SyncDatadisConsumptionsBody(Integer monthsBack) {
        this.monthsBack = monthsBack;
    }

    public SyncDatadisConsumptionsBody(Integer monthsBack, String supplyCode) {
        this.monthsBack = monthsBack;
        this.supplyCode = supplyCode;
    }

    public Integer getMonthsBack() {
        return monthsBack;
    }

    public void setMonthsBack(Integer monthsBack) {
        this.monthsBack = monthsBack;
    }

    public String getSupplyCode() {
        return supplyCode;
    }

    public void setSupplyCode(String supplyCode) {
        this.supplyCode = supplyCode;
    }

    /**
     * The first day to synchronize: the first day of the month {@link #getMonthsBack()} months before
     * {@code endDate}.
     *
     * @param endDate the last day to synchronize, as {@link #getEndDate(Clock)} returns it
     */
    @JsonIgnore
    public LocalDate getStartDate(LocalDate endDate) {
        return new DatadisSyncWindow(Period.ofMonths(monthsBack)).firstDay(endDate);
    }

    /**
     * The last day to synchronize: today.
     *
     * @param clock the source of today, read in the JVM's default zone
     */
    @JsonIgnore
    public LocalDate getEndDate(Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.systemDefault()));
    }
}
