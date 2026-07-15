package org.lucoenergia.conluz.domain.production.plant.distributorfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of parsing/validating a distributor file: every successfully-parsed entry, and
 * every violation found across all 8 rules (never just the first one). A file is valid iff
 * {@link #getErrors()} is empty.
 */
public class DistributorFileParseResult {

    private final List<DistributorFileEntry> entries;
    private final List<DistributorFileError> errors;

    public DistributorFileParseResult(List<DistributorFileEntry> entries, List<DistributorFileError> errors) {
        this.entries = new ArrayList<>(entries);
        this.errors = new ArrayList<>(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<DistributorFileEntry> getEntries() {
        return List.copyOf(entries);
    }

    public List<DistributorFileError> getErrors() {
        return List.copyOf(errors);
    }
}
