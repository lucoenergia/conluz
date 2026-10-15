package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.config.LockHuaweiConfigRepository;
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
public class LockHuaweiConfigRepositoryDatabase implements LockHuaweiConfigRepository {

    private final HuaweiConfigRepository repository;

    public LockHuaweiConfigRepositoryDatabase(HuaweiConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(UUID plantId) {
        return repository.lockByPlantId(plantId).isPresent();
    }
}
