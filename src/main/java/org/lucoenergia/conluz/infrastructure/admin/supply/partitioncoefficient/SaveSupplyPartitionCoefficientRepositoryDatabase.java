package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class SaveSupplyPartitionCoefficientRepositoryDatabase implements SaveSupplyPartitionCoefficientRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaveSupplyPartitionCoefficientRepositoryDatabase.class);

    private final SupplyPartitionCoefficientJpaRepository jpaRepository;
    private final SupplyRepository supplyRepository;
    private final PlantRepository plantRepository;
    private final SharingAgreementRepository sharingAgreementRepository;
    private final SupplyPartitionCoefficientEntityMapper mapper;

    public SaveSupplyPartitionCoefficientRepositoryDatabase(
            SupplyPartitionCoefficientJpaRepository jpaRepository,
            SupplyRepository supplyRepository,
            PlantRepository plantRepository,
            SharingAgreementRepository sharingAgreementRepository,
            SupplyPartitionCoefficientEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.supplyRepository = supplyRepository;
        this.plantRepository = plantRepository;
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    public SupplyPartitionCoefficient save(SupplyPartitionCoefficient coefficient) {
        SupplyEntity supplyEntity = supplyRepository.findById(coefficient.getSupplyId())
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(coefficient.getSupplyId())));
        PlantEntity plantEntity = plantRepository.findById(coefficient.getPlantId())
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(coefficient.getPlantId())));
        SharingAgreementEntity sharingAgreementEntity = sharingAgreementRepository.findById(coefficient.getSharingAgreementId())
                .orElseThrow(() -> new IllegalStateException(
                        "Sharing agreement " + coefficient.getSharingAgreementId() + " not found while saving a coefficient"));

        SupplyPartitionCoefficientEntity entity = new SupplyPartitionCoefficientEntity();
        entity.setId(coefficient.getId());
        entity.setSupply(supplyEntity);
        entity.setPlant(plantEntity);
        entity.setSharingAgreement(sharingAgreementEntity);
        entity.setCoefficient(coefficient.getCoefficient());
        entity.setValidFrom(coefficient.getValidFrom());
        entity.setValidTo(coefficient.getValidTo());
        entity.setCreatedAt(coefficient.getCreatedAt());

        return mapper.map(jpaRepository.save(entity));
    }

    @Override
    public void closeActivePeriod(UUID supplyId, UUID plantId, Instant validTo) {
        Optional<SupplyPartitionCoefficientEntity> result = jpaRepository.findActiveBySupplyIdAndPlantId(supplyId, plantId);
        if (result.isEmpty()) {
            LOGGER.debug("No active coefficient found for supply {} and plant {}", supplyId, plantId);
            return;
        }
        SupplyPartitionCoefficientEntity activeCoefficient = result.get();
        activeCoefficient.setValidTo(validTo);
        jpaRepository.save(activeCoefficient);
    }

    @Override
    public void syncSupplyPartitionCoefficient(UUID supplyId, BigDecimal newCoefficient) {
        SupplyEntity supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(supplyId)));
        supply.setPartitionCoefficient(newCoefficient.floatValue());
        supplyRepository.save(supply);
    }

    @Override
    public List<SupplyPartitionCoefficient> replaceAllForSharingAgreement(UUID sharingAgreementId,
                                                                            List<SupplyPartitionCoefficient> coefficients) {
        jpaRepository.deleteBySharingAgreementId(sharingAgreementId);

        List<SupplyPartitionCoefficient> saved = new ArrayList<>();
        for (SupplyPartitionCoefficient coefficient : coefficients) {
            saved.add(save(coefficient));
        }
        return saved;
    }
}
