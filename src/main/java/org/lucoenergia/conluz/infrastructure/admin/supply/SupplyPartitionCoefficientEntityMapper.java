package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;

@Component
public class SupplyPartitionCoefficientEntityMapper
        extends BaseMapper<SupplyPartitionCoefficientEntity, SupplyPartitionCoefficient> {

    @Override
    public SupplyPartitionCoefficient map(SupplyPartitionCoefficientEntity entity) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(entity.getId())
                .withSupplyId(entity.getSupply().getId())
                .withCoefficient(entity.getCoefficient())
                .withValidFrom(entity.getValidFrom())
                .withValidTo(entity.getValidTo())
                .withCreatedAt(entity.getCreatedAt())
                .build();
    }
}
