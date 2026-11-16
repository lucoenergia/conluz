package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyOwnershipTransferEntityMapperTest {

    private final SupplyOwnershipTransferEntityMapper mapper = new SupplyOwnershipTransferEntityMapper();

    @Test
    void aRecordMapsToAnEntityAndBackUnchanged() {
        SupplyOwnershipTransfer transfer = new SupplyOwnershipTransfer.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SupplyId.of(UUID.randomUUID()))
                .withPreviousOwnerId(UserId.of(UUID.randomUUID()))
                .withNewOwnerId(UserId.of(UUID.randomUUID()))
                .withEffectiveDate(LocalDate.of(2026, 3, 15))
                .withHistoryIncluded(true)
                .withTransferredBy(UserId.of(UUID.randomUUID()))
                .withTransferredAt(Instant.parse("2026-03-20T09:30:00Z"))
                .build();

        SupplyOwnershipTransferEntity entity = mapper.map(transfer);
        SupplyOwnershipTransfer back = mapper.map(entity);

        assertEquals(transfer.getId(), entity.getId());
        assertEquals(transfer.getSupplyId().getId(), entity.getSupplyId());
        assertEquals(transfer.getPreviousOwnerId().orElseThrow().getId(), entity.getPreviousUserId());
        assertEquals(transfer.getNewOwnerId().getId(), entity.getNewUserId());
        assertEquals(transfer.getEffectiveDate(), entity.getEffectiveDate());
        assertTrue(entity.isHistoryIncluded());
        assertEquals(transfer.getTransferredBy().getId(), entity.getTransferredBy());
        assertEquals(transfer.getTransferredAt(), entity.getTransferredAt());

        assertEquals(transfer.getId(), back.getId());
        assertEquals(transfer.getSupplyId(), back.getSupplyId());
        assertEquals(transfer.getPreviousOwnerId(), back.getPreviousOwnerId());
        assertEquals(transfer.getNewOwnerId(), back.getNewOwnerId());
        assertEquals(transfer.getEffectiveDate(), back.getEffectiveDate());
        assertTrue(back.isHistoryIncluded());
        assertEquals(transfer.getTransferredBy(), back.getTransferredBy());
        assertEquals(transfer.getTransferredAt(), back.getTransferredAt());
    }

    @Test
    void noPreviousOwnerMapsToANullColumnAndBackToEmpty() {
        SupplyOwnershipTransfer transfer = new SupplyOwnershipTransfer.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SupplyId.of(UUID.randomUUID()))
                .withNewOwnerId(UserId.of(UUID.randomUUID()))
                .withEffectiveDate(LocalDate.of(2026, 3, 15))
                .withTransferredBy(UserId.of(UUID.randomUUID()))
                .withTransferredAt(Instant.parse("2026-03-20T09:30:00Z"))
                .build();

        SupplyOwnershipTransferEntity entity = mapper.map(transfer);

        assertNull(entity.getPreviousUserId());
        assertEquals(Optional.empty(), mapper.map(entity).getPreviousOwnerId());
    }
}
