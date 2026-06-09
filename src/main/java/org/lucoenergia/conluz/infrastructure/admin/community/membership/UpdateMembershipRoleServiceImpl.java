package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UpdateMembershipRoleServiceImpl implements UpdateMembershipRoleService {

    private final UpdateMembershipRoleRepository updateMembershipRoleRepository;
    private final GetCommunityRepository getCommunityRepository;

    public UpdateMembershipRoleServiceImpl(UpdateMembershipRoleRepository updateMembershipRoleRepository,
                                           GetCommunityRepository getCommunityRepository) {
        this.updateMembershipRoleRepository = updateMembershipRoleRepository;
        this.getCommunityRepository = getCommunityRepository;
    }

    @Override
    public CommunityMembership updateRole(UUID communityId, UUID userId, CommunityRole role) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        return updateMembershipRoleRepository.updateRole(communityId, userId, role);
    }
}
