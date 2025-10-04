package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

/**
 * See {@link <a href="https://docs.influxdata.com/flux/v0/data-types/basic/duration/">InfluxDB durations</a>}
 */
public class InfluxDuration {

    public static final String HOURLY = "1h";
    public static final String DAILY = "1d";
    public static final String MONTHLY = "30d";
    public static final String YEARLY = "1y";
}
