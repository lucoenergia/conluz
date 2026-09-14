package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.GetSharingAgreementFileSummaryRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Repository
public class GetSharingAgreementFileSummaryRepositoryDatabase implements GetSharingAgreementFileSummaryRepository {

    private final SharingAgreementFileRepository sharingAgreementFileRepository;
    private final SharingAgreementFileEntityMapper fileMapper;

    public GetSharingAgreementFileSummaryRepositoryDatabase(SharingAgreementFileRepository sharingAgreementFileRepository,
                                                             SharingAgreementFileEntityMapper fileMapper) {
        this.sharingAgreementFileRepository = sharingAgreementFileRepository;
        this.fileMapper = fileMapper;
    }

    @Override
    public Optional<SharingAgreementFileSummary> findLatestBySharingAgreementId(UUID sharingAgreementId) {
        return sharingAgreementFileRepository
                .findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc(sharingAgreementId)
                .map(fileMapper::mapSummary);
    }

    @Override
    public List<SharingAgreementFileSummary> findLatestBySharingAgreementIds(Collection<UUID> sharingAgreementIds) {
        if (sharingAgreementIds.isEmpty()) {
            return List.of();
        }
        return sharingAgreementFileRepository.findLatestSummariesBySharingAgreementIds(sharingAgreementIds)
                .stream()
                .map(fileMapper::mapSummary)
                .collect(Collectors.toMap(
                        SharingAgreementFileSummary::getSharingAgreementId,
                        summary -> summary,
                        (a, b) -> compareIdsAsPostgresDoes(a.getId(), b.getId()) >= 0 ? a : b))
                .values()
                .stream()
                .toList();
    }

    /**
     * {@link UUID#compareTo} compares {@code mostSigBits}/{@code leastSigBits} as signed longs, so it
     * disagrees with PostgreSQL's byte-wise (unsigned) {@code uuid} ordering for any pair of ids whose
     * first hex digit differs in its high bit (roughly half of all pairs). This tiebreak must agree
     * with the DB-side {@code ORDER BY id DESC} used by
     * {@link #findLatestBySharingAgreementId} (findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc),
     * or the two methods could resolve the same tie to two different "latest" files.
     */
    private static int compareIdsAsPostgresDoes(UUID a, UUID b) {
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return cmp != 0 ? cmp : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
