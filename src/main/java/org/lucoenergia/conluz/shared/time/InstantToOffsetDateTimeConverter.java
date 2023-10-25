package org.lucoenergia.conluz.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class InstantToOffsetDateTimeConverter {

    @Value("${conluz.time.zone.id}")
    private String zoneId;

    public OffsetDateTime convert(Instant instant) {

        ZoneId zoneId = ZoneId.of(this.zoneId);

        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        return instant.atOffset(zonedDateTime.getOffset());
    }
}
