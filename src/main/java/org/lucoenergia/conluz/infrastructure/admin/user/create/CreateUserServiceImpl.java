package org.lucoenergia.conluz.infrastructure.admin.user.create;


import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CreateUserServiceImpl implements CreateUserService {

    private final CreateUserRepository repository;
    private final CreateMembershipService createMembershipService;

    public CreateUserServiceImpl(CreateUserRepository repository,
                                  CreateMembershipService createMembershipService) {
        this.repository = repository;
        this.createMembershipService = createMembershipService;
    }

    @Override
    public User create(User user) {
        return create(user, null, null);
    }

    @Override
    public User create(User user, UUID communityId, CommunityRole communityRole) {
        user.enable();
        user.initializeUuid();

        User created = repository.create(user);

        CommunityRole targetRole = communityRole != null ? communityRole : CommunityRole.COMMUNITY_MEMBER;
        if (communityId != null) {
            createMembershipService.create(communityId, created.getId(), targetRole);
        }

        return created;
    }
}
