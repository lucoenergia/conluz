package org.lucoenergia.conluz.infrastructure.admin.community.create;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateCommunityServiceImpl implements CreateCommunityService {

    private final CreateCommunityRepository repository;

    public CreateCommunityServiceImpl(CreateCommunityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Community create(Community community) {
        community.initializeUuid();
        return repository.create(community);
    }
}
