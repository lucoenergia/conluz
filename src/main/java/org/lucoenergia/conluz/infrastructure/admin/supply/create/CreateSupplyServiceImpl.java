package org.lucoenergia.conluz.infrastructure.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeAvailability;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeInvalidException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class CreateSupplyServiceImpl implements CreateSupplyService {

    private final CreateSupplyRepository repository;
    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final LockUserRepository lockUserRepository;
    private final SupplyCodeAvailability supplyCodeAvailability;

    public CreateSupplyServiceImpl(CreateSupplyRepository repository, GetUserRepository getUserRepository,
                                   GetMembershipsRepository getMembershipsRepository,
                                   LockUserRepository lockUserRepository,
                                   SupplyCodeAvailability supplyCodeAvailability) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.lockUserRepository = lockUserRepository;
        this.supplyCodeAvailability = supplyCodeAvailability;
    }

    @Override
    public Supply create(Supply supply, UserPersonalId id, UUID communityId) {
        SupplyCode code = SupplyCode.ofCups(supply.getCode()).orElseThrow(SupplyCodeInvalidException::new);

        Optional<User> user = getUserRepository.findByPersonalId(id);
        if (user.isEmpty()) {
            throw new SupplyOwnerNotFoundException();
        }
        // Deleting the owner, or their membership in this community, locks the owner's row first (ACC-009,
        // MEM-029). Locking it here, before the membership check, makes the two wait for each other: the
        // deletion then sees this supply and is refused, or this check finds the owner or membership gone.
        if (!lockUserRepository.lock(UserId.of(user.get().getId()))) {
            throw new SupplyOwnerNotFoundException();
        }
        // The owner must belong to the supply's community (#341). A user who does not is reported
        // exactly like an unknown personalId, so the answer does not reveal that the personalId is
        // registered in another community. Any membership counts, accepted or not, enabled or not:
        // a membership's state governs platform access, not who may own a supply.
        if (getMembershipsRepository.findByUserIdAndCommunityId(user.get().getId(), communityId).isEmpty()) {
            throw new SupplyOwnerNotFoundException();
        }
        supplyCodeAvailability.requireAvailable(code, communityId);

        Supply toCreate = supply.copy().withCode(code.getCode()).build();
        toCreate.enable();
        toCreate.initializeUuid();
        return repository.create(toCreate, UserId.of(user.get().getId()), communityId);
    }
}
