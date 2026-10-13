package org.lucoenergia.conluz.infrastructure.admin.community.membership.lock;

import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
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
public class LockMembershipRepositoryDatabase implements LockMembershipRepository {

    private final CommunityMembershipJpaRepository repository;

    public LockMembershipRepositoryDatabase(CommunityMembershipJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(UserId userId, UUID communityId) {
        return repository.lockByUserIdAndCommunityId(userId.getId(), communityId).isPresent();
    }
}
