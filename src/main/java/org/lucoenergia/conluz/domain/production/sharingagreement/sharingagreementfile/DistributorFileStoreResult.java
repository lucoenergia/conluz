package org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileEntry;

import java.util.List;

/**
 * The outcome of successfully storing a distributor file: the persisted evidence file and the
 * structured entries parsed from it.
 */
public class DistributorFileStoreResult {

    private final SharingAgreementFile file;
    private final List<DistributorFileEntry> entries;

    public DistributorFileStoreResult(SharingAgreementFile file, List<DistributorFileEntry> entries) {
        this.file = file;
        this.entries = List.copyOf(entries);
    }

    public SharingAgreementFile getFile() {
        return file;
    }

    public List<DistributorFileEntry> getEntries() {
        return entries;
    }
}
