package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.state.CommunityStateRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class CommunityStateRepositoryDatabase implements CommunityStateRepository {

    private final CommunityJpaRepository communityJpaRepository;

    public CommunityStateRepositoryDatabase(CommunityJpaRepository communityJpaRepository) {
        this.communityJpaRepository = communityJpaRepository;
    }

    @Override
    public void enable(UUID id) {
        CommunityEntity entity = communityJpaRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        entity.setEnabled(true);
        communityJpaRepository.save(entity);
    }

    @Override
    public void disable(UUID id) {
        CommunityEntity entity = communityJpaRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        entity.setEnabled(false);
        communityJpaRepository.save(entity);
    }
}
