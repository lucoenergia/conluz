package org.lucoenergia.conluz.infrastructure.shared.time;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class InstantToOffsetDateTimeConverter {

    private final TimeConfiguration timeConfiguration;

    public InstantToOffsetDateTimeConverter(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    public OffsetDateTime convert(Instant instant) {

        ZoneId zoneId = timeConfiguration.getZoneId();

        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        return instant.atOffset(zonedDateTime.getOffset());
    }
}
