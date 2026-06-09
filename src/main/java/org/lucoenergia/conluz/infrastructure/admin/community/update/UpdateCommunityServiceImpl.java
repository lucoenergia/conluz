package org.lucoenergia.conluz.infrastructure.admin.community.update;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.update.UpdateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.update.UpdateCommunityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UpdateCommunityServiceImpl implements UpdateCommunityService {

    private final UpdateCommunityRepository updateCommunityRepository;

    public UpdateCommunityServiceImpl(UpdateCommunityRepository updateCommunityRepository) {
        this.updateCommunityRepository = updateCommunityRepository;
    }

    @Override
    public Community update(UUID id, Community updated) {
        return updateCommunityRepository.update(id, updated);
    }
}
