package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Configuration
public class TimeConfiguration {

    @Value("${conluz.time.zone.id}")
    private String zoneId;

    public ZoneId getZoneId() {
        return ZoneId.of(this.zoneId);
    }

    public ZoneOffset getOffset(Instant instant) {
        ZoneId zoneId = getZoneId();

        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        return zonedDateTime.getOffset();
    }
}
