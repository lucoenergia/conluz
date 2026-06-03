package org.lucoenergia.conluz.infrastructure.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional
@Repository
public class GetCommunityRepositoryDatabase implements GetCommunityRepository {

    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public GetCommunityRepositoryDatabase(CommunityJpaRepository communityJpaRepository,
                                          CommunityEntityMapper communityEntityMapper) {
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public Optional<Community> findById(UUID id) {
        Optional<CommunityEntity> entity = communityJpaRepository.findById(id);
        return entity.map(communityEntityMapper::map);
    }

    @Override
    public List<Community> findAll() {
        return communityEntityMapper.mapList(communityJpaRepository.findAll());
    }

    @Override
    public List<Community> findAllByIds(Set<UUID> ids) {
        return communityEntityMapper.mapList(communityJpaRepository.findAllById(ids));
    }
}
