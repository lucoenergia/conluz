package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetCommunityContactChannelsServiceImpl implements GetCommunityContactChannelsService {

    private final GetCommunityContactChannelsRepository repository;

    public GetCommunityContactChannelsServiceImpl(GetCommunityContactChannelsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContactChannel> findByCommunityId(UUID communityId) {
        return repository.findByCommunityId(communityId);
    }
}
