package org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile;

import java.util.List;

/**
 * Raised when one or more supplies carry a code that cannot be normalized into the
 * {@link DistributorFileFormat#CUPS_LENGTH}-character CUPS the distributor file requires.
 *
 * <p>Carries every offending code rather than the first one found, so a community fixing its supply
 * data sees the whole list in one response instead of discovering them one generation at a time.
 */
public class SupplyCupsNotNormalizableException extends RuntimeException {

    private final List<String> codes;

    public SupplyCupsNotNormalizableException(List<String> codes) {
        this.codes = List.copyOf(codes);
    }

    public List<String> getCodes() {
        return codes;
    }
}
