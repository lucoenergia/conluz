package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CommunityStateService {

    private final CommunityJpaRepository repository;

    public CommunityStateService(CommunityJpaRepository repository) {
        this.repository = repository;
    }

    public void enable(UUID id) {
        CommunityEntity entity = repository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        entity.setEnabled(true);
        repository.save(entity);
    }

    public void disable(UUID id) {
        CommunityEntity entity = repository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        entity.setEnabled(false);
        repository.save(entity);
    }
}
