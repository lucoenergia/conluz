package org.lucoenergia.conluz.infrastructure.admin.user.create;


import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.domain.admin.user.create.ImportRowCommunityMismatchException;
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
        return create(user, null, null, false);
    }

    @Override
    public User create(User user, UUID communityId, CommunityRole communityRole) {
        return create(user, communityId, communityRole, true);
    }

    private User create(User user, UUID communityId, CommunityRole communityRole, boolean passwordChosenByCreator) {
        if (passwordChosenByCreator) {
            user.requirePasswordChange();
        }
        user.enable();
        user.initializeUuid();

        User created = repository.create(user);

        CommunityRole targetRole = communityRole != null ? communityRole : CommunityRole.COMMUNITY_MEMBER;
        if (communityId != null) {
            createMembershipService.create(communityId, created.getId(), targetRole);
        }

        return created;
    }

    @Override
    public User createFromImport(User user, String rowCommunityId, UUID importCommunityId,
                                 CommunityRole communityRole) {
        if (rowCommunityId != null && !rowCommunityId.isBlank()
                && !isSameCommunity(rowCommunityId, importCommunityId)) {
            throw new ImportRowCommunityMismatchException();
        }
        return create(user, importCommunityId, communityRole);
    }

    private static boolean isSameCommunity(String rowCommunityId, UUID importCommunityId) {
        try {
            return UUID.fromString(rowCommunityId.trim()).equals(importCommunityId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
