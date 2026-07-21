package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetSharingAgreementServiceImpl implements GetSharingAgreementService {

    private final GetSharingAgreementRepository repository;

    public GetSharingAgreementServiceImpl(GetSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status) {
        return repository.findByPlantId(plantId, status);
    }

    @Override
    public SharingAgreement findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new SharingAgreementNotFoundException(id));
    }
}
