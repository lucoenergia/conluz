package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
@Repository
public class GetSupplyPartitionCoefficientRepositoryDatabase implements GetSupplyPartitionCoefficientRepository {

    private final SupplyPartitionCoefficientJpaRepository jpaRepository;
    private final SupplyPartitionCoefficientEntityMapper mapper;

    public GetSupplyPartitionCoefficientRepositoryDatabase(
            SupplyPartitionCoefficientJpaRepository jpaRepository,
            SupplyPartitionCoefficientEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId) {
        return jpaRepository.findActiveBySupplyId(supplyId).map(mapper::map);
    }

    @Override
    public Optional<SupplyPartitionCoefficient> findBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp) {
        return jpaRepository.findBySupplyIdAtTimestamp(supplyId, timestamp).map(mapper::map);
    }

    @Override
    public Optional<SupplyPartitionCoefficient> findByPlantIdAndSupplyIdAtTimestamp(UUID plantId, UUID supplyId, Instant timestamp) {
        return jpaRepository.findByPlantIdAndSupplyIdAtTimestamp(plantId, supplyId, timestamp).map(mapper::map);
    }

    @Override
    public List<SupplyPartitionCoefficient> findAllBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp) {
        return mapper.mapList(jpaRepository.findAllBySupplyIdAtTimestamp(supplyId, timestamp));
    }

    @Override
    public List<SupplyPartitionCoefficient> findBySupplyIdInRange(UUID supplyId, Instant from, Instant to) {
        return mapper.mapList(jpaRepository.findBySupplyIdInRange(supplyId, from, to));
    }

    @Override
    public List<SupplyPartitionCoefficient> findAllBySupplyIdOrderByValidFromAsc(UUID supplyId) {
        return mapper.mapList(jpaRepository.findAllBySupplyIdOrderByValidFromAsc(supplyId));
    }

    @Override
    public List<SupplyPartitionCoefficient> findAllActiveAtTimestamp(Instant timestamp) {
        return mapper.mapList(jpaRepository.findAllActiveAtTimestamp(timestamp));
    }

    @Override
    public boolean existsBySharingAgreementId(UUID sharingAgreementId) {
        return jpaRepository.existsBySharingAgreementId(sharingAgreementId);
    }
}
