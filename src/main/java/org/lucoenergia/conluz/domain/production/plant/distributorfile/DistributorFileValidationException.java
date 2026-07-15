package org.lucoenergia.conluz.domain.production.plant.distributorfile;

import java.util.List;

/**
 * Raised when a distributor file failed one or more validation rules. Carries every violation
 * found, not just the first one.
 */
public class DistributorFileValidationException extends RuntimeException {

    private final List<DistributorFileError> errors;

    public DistributorFileValidationException(List<DistributorFileError> errors) {
        super("Distributor file failed validation with " + errors.size() + " error(s).");
        this.errors = List.copyOf(errors);
    }

    public List<DistributorFileError> getErrors() {
        return errors;
    }
}
