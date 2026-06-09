package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CreateMembershipServiceImpl implements CreateMembershipService {

    private final CreateMembershipRepository createMembershipRepository;
    private final GetCommunityRepository getCommunityRepository;

    public CreateMembershipServiceImpl(CreateMembershipRepository createMembershipRepository,
                                       GetCommunityRepository getCommunityRepository) {
        this.createMembershipRepository = createMembershipRepository;
        this.getCommunityRepository = getCommunityRepository;
    }

    @Override
    public CommunityMembership create(UUID communityId, UUID userId, CommunityRole role) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        return createMembershipRepository.create(communityId, userId, role);
    }
}
