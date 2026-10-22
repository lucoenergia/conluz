package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class SetCommunityContactChannelsRepositoryDatabase implements SetCommunityContactChannelsRepository {

    private final CommunityContactChannelJpaRepository repository;
    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityContactChannelEntityMapper mapper;

    public SetCommunityContactChannelsRepositoryDatabase(CommunityContactChannelJpaRepository repository,
                                                         CommunityJpaRepository communityJpaRepository,
                                                         CommunityContactChannelEntityMapper mapper) {
        this.repository = repository;
        this.communityJpaRepository = communityJpaRepository;
        this.mapper = mapper;
    }

    /**
     * The old channels are deleted in the database before any new one is inserted. Deleting them through the
     * persistence context would not do: Hibernate flushes inserts before deletes, so the new channels would reach the
     * database on top of the old ones and break the unique position of each channel in its community.
     * {@link CommunityContactChannelJpaRepository#deleteByCommunityId} is a bulk delete for that reason.
     */
    @Override
    public List<ContactChannel> replace(UUID communityId, List<ContactChannel> channels) {
        repository.deleteByCommunityId(communityId);
        CommunityEntity community = communityJpaRepository.getReferenceById(communityId);
        List<CommunityContactChannelEntity> entities = new ArrayList<>(channels.size());
        for (int position = 0; position < channels.size(); position++) {
            entities.add(mapper.map(channels.get(position), community, position));
        }
        return repository.saveAll(entities).stream()
                .map(mapper::map)
                .toList();
    }
}
