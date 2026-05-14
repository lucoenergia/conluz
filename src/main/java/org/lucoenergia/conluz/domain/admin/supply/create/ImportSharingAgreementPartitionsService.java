package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.ImportSharingAgreementPartitionsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImportSharingAgreementPartitionsService {

    ImportSharingAgreementPartitionsResponse importPartitions(SharingAgreementId id, MultipartFile file);
}
