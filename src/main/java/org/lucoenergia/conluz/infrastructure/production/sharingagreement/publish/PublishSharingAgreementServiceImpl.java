package org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileFormat;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

        List<SupplyPartitionCoefficient> coefficients =
                supplyPartitionCoefficientRepository.findAllBySharingAgreementId(sharingAgreementId);
        if (coefficients.isEmpty()) {
            throw new SharingAgreementHasNoCoefficientsException(sharingAgreementId);
        }

        BigDecimal sum = DistributorFileFormat.normalizedSum(
                coefficients.stream().map(SupplyPartitionCoefficient::getCoefficient).toList());
        if (!DistributorFileFormat.isValidSum(sum)) {
            throw new SharingAgreementCoefficientSumInvalidException(sharingAgreementId, sum);
        }

        return repository.publish(plantId, sharingAgreementId);
    }
}
