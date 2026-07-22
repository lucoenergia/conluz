package org.lucoenergia.conluz.infrastructure.production.plant.publish;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.publish.PublishSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class PublishSharingAgreementServiceImpl implements PublishSharingAgreementService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    private final PublishSharingAgreementRepository repository;

    public PublishSharingAgreementServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                               GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository,
                                               PublishSharingAgreementRepository repository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.supplyPartitionCoefficientRepository = supplyPartitionCoefficientRepository;
        this.repository = repository;
    }

    @Override
    public SharingAgreement publish(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        agreement.assertDraft();
        if (!supplyPartitionCoefficientRepository.existsBySharingAgreementId(sharingAgreementId)) {
            throw new SharingAgreementHasNoCoefficientsException(sharingAgreementId);
        }
        return repository.publish(plantId, sharingAgreementId);
    }
}
