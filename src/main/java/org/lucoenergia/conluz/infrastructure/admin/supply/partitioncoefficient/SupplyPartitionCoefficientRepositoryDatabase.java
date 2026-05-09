package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class SupplyPartitionCoefficientRepositoryDatabase implements SupplyPartitionCoefficientRepository {

    private final SupplyPartitionCoefficientJpaRepository jpaRepository;
    private final SupplyRepository supplyRepository;
    private final SupplyPartitionCoefficientEntityMapper mapper;

    public SupplyPartitionCoefficientRepositoryDatabase(
            SupplyPartitionCoefficientJpaRepository jpaRepository,
            SupplyRepository supplyRepository,
            SupplyPartitionCoefficientEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.supplyRepository = supplyRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId) {
        return jpaRepository.findActiveBySupplyId(supplyId).map(mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplyPartitionCoefficient> findBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp) {
        return jpaRepository.findBySupplyIdAtTimestamp(supplyId, timestamp).map(mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> findBySupplyIdInRange(UUID supplyId, Instant from, Instant to) {
        return mapper.mapList(jpaRepository.findBySupplyIdInRange(supplyId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> findAllBySupplyIdOrderByValidFromAsc(UUID supplyId) {
        return mapper.mapList(jpaRepository.findAllBySupplyIdOrderByValidFromAsc(supplyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> findAllActiveAtTimestamp(Instant timestamp) {
        return mapper.mapList(jpaRepository.findAllActiveAtTimestamp(timestamp));
    }

    @Override
    public SupplyPartitionCoefficient save(SupplyPartitionCoefficient coefficient) {
        SupplyEntity supplyEntity = supplyRepository.findById(coefficient.getSupplyId())
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(coefficient.getSupplyId())));

        SupplyPartitionCoefficientEntity entity = new SupplyPartitionCoefficientEntity();
        entity.setId(coefficient.getId());
        entity.setSupply(supplyEntity);
        entity.setCoefficient(coefficient.getCoefficient());
        entity.setValidFrom(coefficient.getValidFrom());
        entity.setValidTo(coefficient.getValidTo());
        entity.setCreatedAt(coefficient.getCreatedAt());

        return mapper.map(jpaRepository.save(entity));
    }

    @Override
    public void closeActivePeriod(UUID supplyId, Instant validTo) {
        jpaRepository.closeActivePeriod(supplyId, validTo);
    }
}
