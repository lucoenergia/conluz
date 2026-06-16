package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface SupplyAccessGuard {

    boolean canReadSupply(UUID supplyId);

    boolean canEditSupply(UUID supplyId);
}
