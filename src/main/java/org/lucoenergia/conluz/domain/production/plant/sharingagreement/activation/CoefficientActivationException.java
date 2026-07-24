package org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation;

import java.util.List;

/**
 * Raised when one or more items of an activate/deactivate/close/reopen batch fail validation.
 * Carries every violation found, not just the first -- nothing is written when this is thrown (the
 * whole batch is validated before any write is staged), so "failure on any item rolls back the whole
 * batch" holds by construction, not only via {@code @Transactional}.
 */
public class CoefficientActivationException extends RuntimeException {

    private final List<CoefficientActivationError> errors;

    public CoefficientActivationException(List<CoefficientActivationError> errors) {
        super("Coefficient activation batch failed validation with " + errors.size() + " error(s).");
        this.errors = List.copyOf(errors);
    }

    public List<CoefficientActivationError> getErrors() {
        return errors;
    }
}
