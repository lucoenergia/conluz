package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.DownloadSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class DownloadSharingAgreementFileServiceImpl implements DownloadSharingAgreementFileService {

    private final GetSharingAgreementFileRepository getSharingAgreementFileRepository;

    public DownloadSharingAgreementFileServiceImpl(GetSharingAgreementFileRepository getSharingAgreementFileRepository) {
        this.getSharingAgreementFileRepository = getSharingAgreementFileRepository;
    }

    @Override
    public SharingAgreementFile downloadById(UUID fileId) {
        return getSharingAgreementFileRepository.findById(fileId)
                .orElseThrow(() -> new SharingAgreementFileNotFoundException(fileId));
    }

    @Override
    public SharingAgreementFile downloadLatestBySharingAgreementId(UUID sharingAgreementId) {
        return getSharingAgreementFileRepository.findLatestBySharingAgreementId(sharingAgreementId)
                .orElseThrow(() -> new SharingAgreementFileNotFoundException(sharingAgreementId));
    }
}
