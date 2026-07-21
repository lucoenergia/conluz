package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile.SharingAgreementFileEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile.SharingAgreementFileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class GetSharingAgreementFileRepositoryDatabase implements GetSharingAgreementFileRepository {

    private final SharingAgreementFileRepository sharingAgreementFileRepository;
    private final SharingAgreementFileEntityMapper mapper;

    public GetSharingAgreementFileRepositoryDatabase(SharingAgreementFileRepository sharingAgreementFileRepository,
                                                      SharingAgreementFileEntityMapper mapper) {
        this.sharingAgreementFileRepository = sharingAgreementFileRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SharingAgreementFile> findById(UUID id) {
        return sharingAgreementFileRepository.findById(id).map(mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SharingAgreementFile> findLatestBySharingAgreementId(UUID sharingAgreementId) {
        return sharingAgreementFileRepository
                .findFirstBySharingAgreementIdOrderByUploadedAtDesc(sharingAgreementId)
                .map(mapper::map);
    }
}
