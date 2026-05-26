package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementId;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.io.ImportSharingAgreementPartitionsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImportSharingAgreementPartitionsService {

    ImportSharingAgreementPartitionsResponse importPartitions(SharingAgreementId id, MultipartFile file);
}
