package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class GetCommunityContactChannelsRepositoryDatabase implements GetCommunityContactChannelsRepository {

    private final CommunityContactChannelJpaRepository repository;
    private final CommunityContactChannelEntityMapper mapper;

    public GetCommunityContactChannelsRepositoryDatabase(CommunityContactChannelJpaRepository repository,
                                                         CommunityContactChannelEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ContactChannel> findByCommunityId(UUID communityId) {
        return repository.findAllByCommunityIdOrderByPositionAsc(communityId).stream()
                .map(mapper::map)
                .toList();
    }
}
