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
    }
}
