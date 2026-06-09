package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.lucoenergia.conluz.domain.admin.community.state.CommunityStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CommunityStateService {

    private final CommunityStateRepository repository;

    public CommunityStateService(CommunityStateRepository repository) {
        this.repository = repository;
    }

    public void enable(UUID id) {
        repository.enable(id);
    }

    public void disable(UUID id) {
        repository.disable(id);
    }
}
