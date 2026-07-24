package org.lucoenergia.conluz.infrastructure.shared.time;

import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Wraps the existing application-level {@link TimeConfiguration} -- the same source
 * {@code GetProductionServiceImpl}/{@code GetProductionRepositoryInflux}/{@code DateConverter} already
 * read -- ignoring {@code plantId} for now. When the zone moves to community level, that lookup lands
 * inside this one class with no call site touched.
 */
@Component
public class ZoneResolverImpl implements ZoneResolver {

    private final TimeConfiguration timeConfiguration;

    public ZoneResolverImpl(TimeConfiguration timeConfiguration) {
        this.timeConfiguration = timeConfiguration;
    }

    @Override
    public ZoneId resolveZoneId(UUID plantId) {
        return timeConfiguration.getZoneId();
    }
}
