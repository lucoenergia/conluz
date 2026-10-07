package org.lucoenergia.conluz.infrastructure.admin.user.lock;

import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writable rather than read-only, because PostgreSQL refuses a row lock in a read-only transaction, and mandatory,
 * because a lock taken in a transaction of its own would be released before the caller does anything with it.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockUserRepositoryDatabase implements LockUserRepository {

    private final UserRepository repository;

    public LockUserRepositoryDatabase(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(UserId userId) {
        return repository.lockById(userId.getId()).isPresent();
    }
}
