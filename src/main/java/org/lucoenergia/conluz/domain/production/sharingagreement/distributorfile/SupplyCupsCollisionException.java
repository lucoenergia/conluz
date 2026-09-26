package org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile;

import java.util.List;

/**
 * Raised when two distinct supply codes in the same community normalize to the same CUPS -- one
 * stored at {@link DistributorFileFormat#SHORT_CUPS_LENGTH} characters and another already carrying
 * the {@link DistributorFileFormat#SHORT_CUPS_SUFFIX} completion.
 *
 * <p>Such a pair is ambiguous in both directions: a generated file would carry the same CUPS on two
 * lines, and an uploaded file's CUPS could not be resolved back to a single supply.
 */
public class SupplyCupsCollisionException extends RuntimeException {

    private final String normalizedCups;
    private final List<String> codes;

    public SupplyCupsCollisionException(String normalizedCups, List<String> codes) {
        this.normalizedCups = normalizedCups;
        this.codes = List.copyOf(codes);
    }

    public String getNormalizedCups() {
        return normalizedCups;
    }

    public List<String> getCodes() {
        return codes;
    }
}
