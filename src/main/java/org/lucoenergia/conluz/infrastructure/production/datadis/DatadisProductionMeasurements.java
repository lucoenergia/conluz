package org.lucoenergia.conluz.infrastructure.production.datadis;

/**
 * InfluxDB measurement names for Datadis-derived production time-series. Kept separate from the
 * consumption {@code DatadisConfigEntity} constants so the production package does not depend on
 * consumption infrastructure.
 */
public final class DatadisProductionMeasurements {

    public static final String PRODUCTION_KWH_MEASUREMENT = "datadis_production_kwh";
    public static final String PRODUCTION_KWH_MONTH_MEASUREMENT = "datadis_production_kwh_month";
    public static final String PRODUCTION_KWH_YEAR_MEASUREMENT = "datadis_production_kwh_year";

    private DatadisProductionMeasurements() {
    }
}
