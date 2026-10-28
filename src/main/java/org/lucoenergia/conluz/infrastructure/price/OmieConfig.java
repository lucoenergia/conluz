package org.lucoenergia.conluz.infrastructure.price;

import java.time.ZoneId;

public class OmieConfig {

    private OmieConfig() {
    }

    public static final String PRICES_KWH_MEASUREMENT = "omie_prices_kwh";

    /**
     * Time zone of the OMIE market day, whatever zone the application is configured with.
     */
    public static final ZoneId OMIE_ZONE = ZoneId.of("Europe/Madrid");
}
