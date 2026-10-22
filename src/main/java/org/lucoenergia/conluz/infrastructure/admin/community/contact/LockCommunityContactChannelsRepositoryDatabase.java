package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.LockCommunityContactChannelsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writable rather than read-only, because PostgreSQL refuses a row lock in a read-only transaction, and mandatory,
 * because a lock taken in a transaction of its own would be released before the caller does anything with it.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockCommunityContactChannelsRepositoryDatabase implements LockCommunityContactChannelsRepository {

    private final CommunityContactChannelJpaRepository repository;

    public LockCommunityContactChannelsRepositoryDatabase(CommunityContactChannelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void lock(UUID communityId) {
        repository.lockCommunity(communityId);
    }
}
