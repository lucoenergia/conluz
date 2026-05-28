package org.lucoenergia.conluz.infrastructure.admin.user.create;


import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.infrastructure.shared.security.community.CommunityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CreateUserServiceImpl implements CreateUserService {

    private final CreateUserRepository repository;
    private final AuthService authService;
    private final CommunityContext communityContext;
    private final CreateMembershipService createMembershipService;

    public CreateUserServiceImpl(CreateUserRepository repository,
                                  AuthService authService,
                                  CommunityContext communityContext,
                                  CreateMembershipService createMembershipService) {
        this.repository = repository;
        this.authService = authService;
        this.communityContext = communityContext;
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

        UUID targetCommunityId = communityId;
        CommunityRole targetRole = communityRole != null ? communityRole : CommunityRole.COMMUNITY_MEMBER;

        if (targetCommunityId == null) {
            User caller = authService.getCurrentUser().orElse(null);
            if (caller != null && !caller.isPlatformAdmin() && caller.getRole() != org.lucoenergia.conluz.domain.admin.user.Role.ADMIN) {
                targetCommunityId = communityContext.getActiveCommunityId().orElse(null);
            }
        }

        if (targetCommunityId != null) {
            createMembershipService.create(targetCommunityId, created.getId(), targetRole);
        }

        return created;
    }
}
