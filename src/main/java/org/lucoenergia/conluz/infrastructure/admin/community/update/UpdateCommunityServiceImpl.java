package org.lucoenergia.conluz.infrastructure.admin.community.update;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.update.UpdateCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UpdateCommunityServiceImpl implements UpdateCommunityService {

    private final CommunityJpaRepository communityJpaRepository;
    private final GetCommunityRepository getCommunityRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public UpdateCommunityServiceImpl(CommunityJpaRepository communityJpaRepository,
                                      GetCommunityRepository getCommunityRepository,
                                      CommunityEntityMapper communityEntityMapper) {
        this.communityJpaRepository = communityJpaRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public Community update(UUID id, Community updated) {
        CommunityEntity entity = communityJpaRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));

        entity.setName(updated.getName());
        entity.setCode(updated.getCode());
        if (updated.getLegalId() != null) {
            entity.setLegalId(updated.getLegalId());
        }
        if (updated.getAddress() != null) {
            entity.setAddress(updated.getAddress());
        }

        CommunityEntity saved = communityJpaRepository.save(entity);
        return communityEntityMapper.map(saved);
    }
}
