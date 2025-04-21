package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Component
public class SupplyPartitionEntityMapper extends BaseMapper<SupplyPartitionEntity, SupplyPartition> {

    private final SupplyEntityMapper supplyEntityMapper;
    private final SharingAgreementEntityMapper sharingAgreementEntityMapper;

    public SupplyPartitionEntityMapper(SupplyEntityMapper supplyEntityMapper,
                                       SharingAgreementEntityMapper sharingAgreementEntityMapper) {
        this.supplyEntityMapper = supplyEntityMapper;
        this.sharingAgreementEntityMapper = sharingAgreementEntityMapper;
    }

    @Override
    public SupplyPartition map(SupplyPartitionEntity entity) {
        return new SupplyPartition(
                entity.getId(),
                supplyEntityMapper.map(entity.getSupply()),
                sharingAgreementEntityMapper.map(entity.getSharingAgreement()),
                entity.getCoefficient()
        );
    }
}
