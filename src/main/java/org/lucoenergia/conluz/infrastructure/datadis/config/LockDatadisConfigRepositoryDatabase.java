package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.lucoenergia.conluz.domain.datadis.LockDatadisConfigRepository;
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
public class LockDatadisConfigRepositoryDatabase implements LockDatadisConfigRepository {

    private final DatadisConfigRepository repository;

    public LockDatadisConfigRepositoryDatabase(DatadisConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(UUID communityId) {
        return repository.lockByCommunityId(communityId).isPresent();
    }
}
