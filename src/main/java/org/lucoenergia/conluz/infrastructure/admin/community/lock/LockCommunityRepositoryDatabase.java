package org.lucoenergia.conluz.infrastructure.admin.community.lock;

import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
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
public class LockCommunityRepositoryDatabase implements LockCommunityRepository {

    private final CommunityJpaRepository repository;

    public LockCommunityRepositoryDatabase(CommunityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(UUID communityId) {
        return repository.lockById(communityId).isPresent();
    }
}
