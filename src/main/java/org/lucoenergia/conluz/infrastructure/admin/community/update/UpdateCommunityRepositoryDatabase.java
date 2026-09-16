package org.lucoenergia.conluz.infrastructure.admin.community.update;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.update.UpdateCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class UpdateCommunityRepositoryDatabase implements UpdateCommunityRepository {

    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public UpdateCommunityRepositoryDatabase(CommunityJpaRepository communityJpaRepository,
                                             CommunityEntityMapper communityEntityMapper) {
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public Community update(UUID id, Community updated) {
        CommunityEntity entity = communityJpaRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));

        // Checked for the same reason create checks: code and legal_id are unique, and the
        // DataIntegrityViolationException the constraint raises is mapped nowhere, so without this
        // a rename onto a taken value surfaced as a 500. Both checks exclude this community, or
        // keeping its own values would collide with itself.
        if (communityJpaRepository.existsByCodeAndIdNot(updated.getCode(), id)) {
            throw new CommunityAlreadyExistsException("code", updated.getCode());
        }
        if (updated.getLegalId() != null
                && communityJpaRepository.existsByLegalIdAndIdNot(updated.getLegalId(), id)) {
            throw new CommunityAlreadyExistsException("legalId", updated.getLegalId());
        }

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
