package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetMembershipsServiceImpl implements GetMembershipsService {

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetCommunityRepository getCommunityRepository;

    public GetMembershipsServiceImpl(GetMembershipsRepository getMembershipsRepository,
                                     GetCommunityRepository getCommunityRepository) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getCommunityRepository = getCommunityRepository;
    }

    @Override
    public List<CommunityMembership> findByCommunityId(UUID communityId) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        return getMembershipsRepository.findByCommunityId(communityId);
    }
}
