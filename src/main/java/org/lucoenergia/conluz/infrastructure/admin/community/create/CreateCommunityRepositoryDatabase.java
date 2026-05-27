package org.lucoenergia.conluz.infrastructure.admin.community.create;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class CreateCommunityRepositoryDatabase implements CreateCommunityRepository {

    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public CreateCommunityRepositoryDatabase(CommunityJpaRepository communityJpaRepository,
                                             CommunityEntityMapper communityEntityMapper) {
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public Community create(Community community) {
        if (communityJpaRepository.existsByCode(community.getCode())) {
            throw new CommunityAlreadyExistsException("code", community.getCode());
        }
        if (community.getLegalId() != null && communityJpaRepository.existsByLegalId(community.getLegalId())) {
            throw new CommunityAlreadyExistsException("legalId", community.getLegalId());
        }

        CommunityEntity entity = new CommunityEntity.Builder()
                .withId(UUID.randomUUID())
                .withName(community.getName())
                .withCode(community.getCode())
                .withLegalId(community.getLegalId())
                .withAddress(community.getAddress())
                .withEnabled(community.isEnabled())
                .build();

        CommunityEntity saved = communityJpaRepository.save(entity);
        return communityEntityMapper.map(saved);
    }
}
