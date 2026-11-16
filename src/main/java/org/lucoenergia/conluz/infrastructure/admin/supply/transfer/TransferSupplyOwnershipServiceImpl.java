package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.CreateSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.GetSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyAlreadyOwnedByUserException;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyTransferDateInFutureException;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyTransferDateNotAfterPreviousException;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyTransferOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.transfer.TransferSupplyOwnershipService;
import org.lucoenergia.conluz.domain.admin.supply.transfer.UpdateSupplyOwnerRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Transactional
@Service
public class TransferSupplyOwnershipServiceImpl implements TransferSupplyOwnershipService {

    private final GetSupplyRepository getSupplyRepository;
    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyOwnershipTransferRepository getTransferRepository;
    private final LockCommunityRepository lockCommunityRepository;
    private final LockUserRepository lockUserRepository;
    private final LockSupplyRepository lockSupplyRepository;
    private final UpdateSupplyOwnerRepository updateSupplyOwnerRepository;
    private final CreateSupplyOwnershipTransferRepository createTransferRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public TransferSupplyOwnershipServiceImpl(GetSupplyRepository getSupplyRepository,
                                              GetUserRepository getUserRepository,
                                              GetMembershipsRepository getMembershipsRepository,
                                              GetSupplyOwnershipTransferRepository getTransferRepository,
                                              LockCommunityRepository lockCommunityRepository,
                                              LockUserRepository lockUserRepository,
                                              LockSupplyRepository lockSupplyRepository,
                                              UpdateSupplyOwnerRepository updateSupplyOwnerRepository,
                                              CreateSupplyOwnershipTransferRepository createTransferRepository,
                                              ZoneResolver zoneResolver,
                                              Clock clock) {
        this.getSupplyRepository = getSupplyRepository;
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.getTransferRepository = getTransferRepository;
        this.lockCommunityRepository = lockCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockSupplyRepository = lockSupplyRepository;
        this.updateSupplyOwnerRepository = updateSupplyOwnerRepository;
        this.createTransferRepository = createTransferRepository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public Supply transfer(SupplyId supplyId, UserPersonalId newOwner, LocalDate effectiveDate,
                           UserId transferredBy) {
        // A supply never moves to another community, so the community read before any lock is the one to lock
        UUID communityId = findSupply(supplyId).getCommunity().getId();
        // Every transfer locks the community first, and no other flow writes a supply's owner, so the owner read
        // below cannot change before this transaction ends (SUP-009)
        if (!lockCommunityRepository.lock(communityId)) {
            throw new SupplyNotFoundException(supplyId);
        }

        User user = getUserRepository.findByPersonalId(newOwner)
                .orElseThrow(SupplyTransferOwnerNotFoundException::new);
        UUID newOwnerId = user.getId();
        UUID previousOwnerId = ownerIdOf(findSupply(supplyId)).orElse(null);

        // Deleting an owner, or their membership, locks the owner's row first (ACC-009, MEM-029). Both owners'
        // rows are locked before the membership check, in ascending id order so that two transfers between the
        // same users cannot deadlock: a deletion then sees the supply and is refused, or this transfer finds the
        // new owner's membership gone.
        if (!lockUsers(newOwnerId, previousOwnerId)) {
            throw new SupplyTransferOwnerNotFoundException();
        }
        // Any membership counts, accepted or not, enabled or not, as when a supply is created (SUP-002). A user
        // without one is answered like an unknown personal ID, so the answer does not reveal that it is
        // registered in another community.
        if (getMembershipsRepository.findByUserIdAndCommunityId(newOwnerId, communityId).isEmpty()) {
            throw new SupplyTransferOwnerNotFoundException();
        }
        // The record references the supply: it waits for a deletion in progress and blocks one until this commits
        if (!lockSupplyRepository.lockAsReferenced(supplyId)) {
            throw new SupplyNotFoundException(supplyId);
        }

        if (newOwnerId.equals(previousOwnerId)) {
            throw new SupplyAlreadyOwnedByUserException();
        }
        LocalDate today = LocalDate.now(clock.withZone(zoneResolver.resolveZoneIdForCommunity(communityId)));
        if (effectiveDate.isAfter(today)) {
            throw new SupplyTransferDateInFutureException();
        }
        Optional<LocalDate> previousDate = getTransferRepository.findLatestEffectiveDate(supplyId);
        if (previousDate.isPresent() && !effectiveDate.isAfter(previousDate.get())) {
            throw new SupplyTransferDateNotAfterPreviousException();
        }

        if (!updateSupplyOwnerRepository.updateOwner(supplyId, UserId.of(newOwnerId))) {
            throw new SupplyNotFoundException(supplyId);
        }
        createTransferRepository.create(new SupplyOwnershipTransfer.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPreviousOwnerId(previousOwnerId != null ? UserId.of(previousOwnerId) : null)
                .withNewOwnerId(UserId.of(newOwnerId))
                .withEffectiveDate(effectiveDate)
                // Withholding the history from the new owner is not supported: every transfer carries it (SUP-012)
                .withHistoryIncluded(true)
                .withTransferredBy(transferredBy)
                .withTransferredAt(clock.instant())
                .build());

        return findSupply(supplyId);
    }

    private Supply findSupply(SupplyId supplyId) {
        return getSupplyRepository.findById(supplyId).orElseThrow(() -> new SupplyNotFoundException(supplyId));
    }

    private static Optional<UUID> ownerIdOf(Supply supply) {
        return Optional.ofNullable(supply.getUser()).map(User::getId);
    }

    /**
     * Locks the new owner's row and, if the supply has one, the previous owner's, in ascending id order. The order is
     * PostgreSQL's order of UUIDs, which is the order of their text form; {@link UUID#compareTo} compares signed
     * numbers and differs from it.
     *
     * @return {@code false} if the new owner no longer exists
     */
    private boolean lockUsers(UUID newOwnerId, UUID previousOwnerId) {
        TreeSet<UUID> ids = new TreeSet<>(Comparator.comparing(UUID::toString));
        ids.add(newOwnerId);
        if (previousOwnerId != null) {
            ids.add(previousOwnerId);
        }
        boolean newOwnerLocked = false;
        for (UUID id : ids) {
            boolean locked = lockUserRepository.lock(UserId.of(id));
            if (id.equals(newOwnerId)) {
                newOwnerLocked = locked;
            }
        }
        return newOwnerLocked;
    }
}
