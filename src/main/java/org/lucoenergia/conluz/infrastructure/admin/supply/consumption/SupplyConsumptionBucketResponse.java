package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;

/**
 * One bucket of a supply's consumption series -- a local calendar day on the daily endpoint, a local
 * calendar month on the monthly one.
 *
 * <p>The two granularities share this shape because they carry the same fields with the same
 * meaning; only the width of the bucket differs, and the caller already chose it through the URL.
 *
 * <p>This is the response contract, deliberately separate from {@link DatadisConsumption}, which is
 * the shape the external Datadis API speaks and is still serialised directly by the hourly and
 * yearly endpoints. Detaching them drops {@code empty} -- a boolean that never existed as data and
 * only appeared in the JSON because {@code isEmpty()} is a getter.
 */
@Schema(requiredProperties = {"cups", "date", "time", "consumptionKWh", "obtainMethod",
        "surplusEnergyKWh", "generationEnergyKWh", "selfConsumptionEnergyKWh"})
public class SupplyConsumptionBucketResponse {

    @Schema(description = "CUPS code of the supply the bucket belongs to.",
            example = "ES0031406912345678JN0F")
    private final String cups;
    @Schema(description = "Local calendar date the bucket starts on: the day itself on the daily " +
            "series, the first day of the month on the monthly one.",
            example = "2023/04/10")
    private final String date;
    @Schema(description = "Local time the bucket starts at, in the zone the application is " +
            "configured with.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy consumed from the grid over the bucket, in kWh.", example = "12.5")
    private final Float consumptionKWh;
    @Schema(description = "How the underlying records were obtained, as reported by Datadis. Null " +
            "on a bucket with no stored record.",
            example = "Real", types = {"string", "null"})
    private final String obtainMethod;
    @Schema(description = "Energy exported to the grid over the bucket, in kWh.", example = "3.25")
    private final Float surplusEnergyKWh;
    @Schema(description = "Energy generated and assigned to the supply over the bucket, in kWh.",
            example = "8.75")
    private final Float generationEnergyKWh;
    @Schema(description = "Energy generated and consumed on site over the bucket, in kWh.",
            example = "5.5")
    private final Float selfConsumptionEnergyKWh;

    public SupplyConsumptionBucketResponse(DatadisConsumption consumption) {
        this.cups = consumption.getCups();
        this.date = consumption.getDate();
        this.time = consumption.getTime();
        this.consumptionKWh = consumption.getConsumptionKWh();
        this.obtainMethod = consumption.getObtainMethod();
        this.surplusEnergyKWh = consumption.getSurplusEnergyKWh();
        this.generationEnergyKWh = consumption.getGenerationEnergyKWh();
        this.selfConsumptionEnergyKWh = consumption.getSelfConsumptionEnergyKWh();
    }

    public String getCups() {
        return cups;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public Float getConsumptionKWh() {
        return consumptionKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }

    public Float getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public Float getGenerationEnergyKWh() {
        return generationEnergyKWh;
    }

    public Float getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
    }
}
