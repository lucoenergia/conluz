package org.lucoenergia.conluz.domain.production.huawei.get;

import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;

import java.time.Instant;

public interface GetHuaweiProductionRepository {

    /**
     * Reads the real-time snapshots of one station around the current local day.
     *
     * @param stationCode      the station the snapshots are stored under: the plant's provider code
     * @param previousDayStart the start of the previous local day, inclusive
     * @param todayStart       the start of the current local day: the end of the previous one, exclusive, and the
     *                         start of today, inclusive
     * @param now              the end of today's snapshots, exclusive
     * @return the {@code day_power} of the last snapshot of the previous day, null when there is none, and today's
     * snapshots in time order
     */
    InverterProductionDay getProductionDay(String stationCode, Instant previousDayStart, Instant todayStart,
                                           Instant now);
}
