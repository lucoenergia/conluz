package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface CreateSharingAgreementWithPartitionsService {

    SharingAgreement create(LocalDate startDate, LocalDate endDate, String notes, MultipartFile file);
}
