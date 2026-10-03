package org.lucoenergia.conluz.infrastructure.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
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

    public CreateSupplyServiceImpl(CreateSupplyRepository repository, GetUserRepository getUserRepository,
                                   GetMembershipsRepository getMembershipsRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Override
    public Supply create(Supply supply, UserPersonalId id, UUID communityId) {
        Optional<User> user = getUserRepository.findByPersonalId(id);
        if (user.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        // The owner must belong to the supply's community (#341). A user who does not is reported
        // exactly like an unknown personalId, so the answer does not reveal that the personalId is
        // registered in another community. Any membership counts, enabled or not: disabling a
        // membership governs platform access, not who may own a supply.
        if (getMembershipsRepository.findByUserIdAndCommunityId(user.get().getId(), communityId).isEmpty()) {
            throw new UserNotFoundException(id);
        }
        supply.enable();
        supply.initializeUuid();
        return repository.create(supply, UserId.of(user.get().getId()), communityId);
    }
}
