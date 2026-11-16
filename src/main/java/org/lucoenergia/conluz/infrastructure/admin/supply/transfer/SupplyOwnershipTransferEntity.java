package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A record of a supply ownership transfer. It is {@link Immutable}, so Hibernate never writes an update for it: a
 * record is inserted once and then only read (SUP-011). The references to the supply and the users are plain
 * identifiers, so the entity has no association that could cascade.
 */
@Immutable
@Entity(name = "supply_ownership_transfers")
public class SupplyOwnershipTransferEntity {

    @Id
    private UUID id;

    @Column(name = "supply_id", nullable = false)
    private UUID supplyId;

    @Column(name = "previous_user_id")
    private UUID previousUserId;

    @Column(name = "new_user_id", nullable = false)
    private UUID newUserId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "history_included", nullable = false)
    private boolean historyIncluded;

    @Column(name = "transferred_by", nullable = false)
    private UUID transferredBy;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public void setSupplyId(UUID supplyId) {
        this.supplyId = supplyId;
    }

    public UUID getPreviousUserId() {
        return previousUserId;
    }

    public void setPreviousUserId(UUID previousUserId) {
        this.previousUserId = previousUserId;
    }

    public UUID getNewUserId() {
        return newUserId;
    }

    public void setNewUserId(UUID newUserId) {
        this.newUserId = newUserId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public boolean isHistoryIncluded() {
        return historyIncluded;
    }

    public void setHistoryIncluded(boolean historyIncluded) {
        this.historyIncluded = historyIncluded;
    }

    public UUID getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(UUID transferredBy) {
        this.transferredBy = transferredBy;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(Instant transferredAt) {
        this.transferredAt = transferredAt;
    }
}
