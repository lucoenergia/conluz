package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.error.PostgresConstraintNameChecker;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The database backstop for "a supply is the supply of at most one plant" (PLT-002). The plant
 * writers check for a plant on the supply before writing, but two concurrent writers can both pass
 * that check; the second write is then rejected by this constraint, and must be reported as the same
 * conflict rather than as an unmapped integrity error (a 500).
 */
public final class PlantSupplyUniqueConstraint {

    public static final String NAME = "plants_supply_id_uq";

    private PlantSupplyUniqueConstraint() {
    }

    /**
     * Translates a violation of this specific constraint, identified by its name, into
     * {@link SupplyAlreadyHasPlantException} for the given supply. Any other integrity violation is
     * returned unchanged, so the caller rethrows it as it was.
     */
    public static RuntimeException translate(DataIntegrityViolationException exception, SupplyId supplyId) {
        if (PostgresConstraintNameChecker.matches(exception, NAME)) {
            return new SupplyAlreadyHasPlantException(supplyId, exception);
        }
        return exception;
    }
}
