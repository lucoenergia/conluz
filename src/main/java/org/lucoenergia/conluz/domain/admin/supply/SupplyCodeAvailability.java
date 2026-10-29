package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.UUID;

/**
 * Whether a CUPS can be written to a supply of a community: no other supply may have the same CUPS once both codes
 * are completed to 22 characters and compared case-insensitively (#532). The check is application-level and takes no
 * lock, so two writes of the same CUPS running at the same time can both pass it.
 */
public interface SupplyCodeAvailability {

    /**
     * For a supply being created.
     *
     * @param code a code from {@link SupplyCode#ofCups}
     * @throws SupplyAlreadyExistsException                 if a supply of {@code communityId} has the same CUPS
     * @throws SupplyRegisteredInAnotherCommunityException if only supplies of other communities have it
     */
    void requireAvailable(SupplyCode code, UUID communityId);

    /**
     * For a supply whose code is being changed: the supply itself is not compared.
     *
     * @param code a code from {@link SupplyCode#ofCups}
     * @throws SupplyAlreadyExistsException                 if another supply of {@code communityId} has the same CUPS
     * @throws SupplyRegisteredInAnotherCommunityException if only supplies of other communities have it
     */
    void requireAvailable(SupplyCode code, UUID communityId, SupplyId supplyId);
}
