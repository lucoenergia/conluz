package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Component;

@Component
public class SupplyOwnershipTransferEntityMapper {

    public SupplyOwnershipTransfer map(SupplyOwnershipTransferEntity entity) {
        return new SupplyOwnershipTransfer.Builder()
                .withId(entity.getId())
                .withSupplyId(SupplyId.of(entity.getSupplyId()))
                .withPreviousOwnerId(entity.getPreviousUserId() != null ? UserId.of(entity.getPreviousUserId()) : null)
                .withNewOwnerId(UserId.of(entity.getNewUserId()))
                .withEffectiveDate(entity.getEffectiveDate())
                .withHistoryIncluded(entity.isHistoryIncluded())
                .withTransferredBy(UserId.of(entity.getTransferredBy()))
                .withTransferredAt(entity.getTransferredAt())
                .build();
    }

    public SupplyOwnershipTransferEntity map(SupplyOwnershipTransfer transfer) {
        SupplyOwnershipTransferEntity entity = new SupplyOwnershipTransferEntity();
        entity.setId(transfer.getId());
        entity.setSupplyId(transfer.getSupplyId().getId());
        entity.setPreviousUserId(transfer.getPreviousOwnerId().map(UserId::getId).orElse(null));
        entity.setNewUserId(transfer.getNewOwnerId().getId());
        entity.setEffectiveDate(transfer.getEffectiveDate());
        entity.setHistoryIncluded(transfer.isHistoryIncluded());
        entity.setTransferredBy(transfer.getTransferredBy().getId());
        entity.setTransferredAt(transfer.getTransferredAt());
        return entity;
    }
}
