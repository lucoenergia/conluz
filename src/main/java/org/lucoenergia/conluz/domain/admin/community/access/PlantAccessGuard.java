package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface PlantAccessGuard {

    boolean canManagePlant(UUID plantId);

    /**
     * Whether the current user may read the given plant. Allowed for any
     * enabled member (regardless of role) of the plant's community.
     */
    boolean canReadPlant(UUID plantId);

    boolean canCreatePlant(String supplyCode);

    boolean canListPlants(UUID communityId);
}
