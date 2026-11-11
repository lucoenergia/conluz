package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.PlantCilAlreadyInUseException;
import org.lucoenergia.conluz.infrastructure.shared.error.PostgresConstraintNameChecker;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The database backstop for "a CIL identifies at most one plant" (PLT-005). The plant writers check for a plant with
 * the CIL before writing, but two concurrent writers can both pass that check; the second write is then rejected by
 * this constraint, and must be reported as the same conflict rather than as an unmapped integrity error (a 500).
 */
public final class PlantCilUniqueConstraint {

    public static final String NAME = "plants_cil_uq";

    private PlantCilUniqueConstraint() {
    }

    /**
     * Translates a violation of this specific constraint, identified by its name, into
     * {@link PlantCilAlreadyInUseException}. Any other integrity violation is returned unchanged, so the caller
     * rethrows it as it was.
     */
    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (PostgresConstraintNameChecker.matches(exception, NAME)) {
            return new PlantCilAlreadyInUseException(exception);
        }
        return exception;
    }
}
