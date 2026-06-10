package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface PlantAccessGuard {

    boolean canManagePlant(UUID plantId);

    boolean canCreatePlant(String supplyCode);
}
