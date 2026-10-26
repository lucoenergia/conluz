package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import jakarta.persistence.EntityManager;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LockPlatformAdminsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A PostgreSQL transaction-level advisory lock, released when the caller's transaction ends. It conflicts only with
 * itself, so it never blocks a flow that does not take it.
 *
 * <p>Mandatory, because a lock taken in a transaction of its own would be released before the caller does anything
 * with it.</p>
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockPlatformAdminsRepositoryDatabase implements LockPlatformAdminsRepository {

    /**
     * The advisory lock key of the platform-admin set. No other advisory lock is used by the application; a new one
     * must take a different key.
     */
    static final long PLATFORM_ADMINS_LOCK_KEY = 0x636F6E6C757A01L;

    private final EntityManager entityManager;

    public LockPlatformAdminsRepositoryDatabase(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void lock() {
        // pg_advisory_xact_lock returns void, which has no JDBC mapping: the count wraps it in a readable result
        entityManager.createNativeQuery("SELECT count(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS platform_admins")
                .setParameter("key", PLATFORM_ADMINS_LOCK_KEY)
                .getSingleResult();
    }
}
