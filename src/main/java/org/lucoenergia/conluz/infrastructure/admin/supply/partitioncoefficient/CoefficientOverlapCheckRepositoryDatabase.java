package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import jakarta.persistence.EntityManager;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientOverlapCheckRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientOverlapException;
import org.lucoenergia.conluz.infrastructure.shared.error.PostgresConstraintNameChecker;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class CoefficientOverlapCheckRepositoryDatabase implements CoefficientOverlapCheckRepository {

    public static final String CONSTRAINT_NAME = "no_overlapping_coefficients";

    private final EntityManager entityManager;

    public CoefficientOverlapCheckRepositoryDatabase(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void flushAndCheckNoOverlap() {
        entityManager.flush();
        try {
            entityManager.createNativeQuery("SET CONSTRAINTS " + CONSTRAINT_NAME + " IMMEDIATE").executeUpdate();
        } catch (RuntimeException e) {
            // Caught here as the raw JPA/Hibernate exception, not yet DataIntegrityViolationException:
            // @Repository's exception translation runs on the AOP proxy boundary, which this method
            // body is inside of, not past. PostgresConstraintName walks the full cause chain regardless
            // of the top-level wrapper type, so it works the same either way.
            if (PostgresConstraintNameChecker.matches(e, CONSTRAINT_NAME)) {
                throw new SupplyPartitionCoefficientOverlapException(e);
            }
            throw e;
        }
        // Back to DEFERRED before returning. SET CONSTRAINTS lasts for the rest of the transaction, so
        // without this the check would silently revoke the deferral for every later write in the same
        // transaction -- and a cascade's intermediate state (two open-ended rows for one supply between
        // its two UPDATEs) is only legal while the constraint is deferred. A second cascading batch in
        // one transaction would then fail on a transaction whose final state is consistent, which is
        // exactly what ADR-0001 deferred the constraint to prevent. Restoring it keeps this method
        // self-contained: it resolves the checks staged so far and leaves the mode as it found it.
        //
        // Only on the success path: the catch above rethrows as a typed 409 and the transaction is
        // being rolled back, so the session's constraint mode no longer matters there.
        entityManager.createNativeQuery("SET CONSTRAINTS " + CONSTRAINT_NAME + " DEFERRED").executeUpdate();
    }
}
