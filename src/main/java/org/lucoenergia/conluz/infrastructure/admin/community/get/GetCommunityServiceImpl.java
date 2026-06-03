package org.lucoenergia.conluz.infrastructure.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCommunityServiceImpl implements GetCommunityService {

    private final GetCommunityRepository repository;
    private final CommunityAccessGuard guard;

    public GetCommunityServiceImpl(GetCommunityRepository repository, CommunityAccessGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    @Override
    public Optional<Community> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Community> findAllVisible() {
        Set<UUID> visibleIds = guard.visibleCommunityIds();
        if (visibleIds == null) {
            return repository.findAll();
        }
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIds(visibleIds);
    }
}
