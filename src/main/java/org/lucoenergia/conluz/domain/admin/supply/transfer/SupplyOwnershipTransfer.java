package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The record of one change of a supply's owner (SUP-011). It is written once, when the transfer is made, and never
 * changed afterwards.
 */
public class SupplyOwnershipTransfer {

    private final UUID id;
    private final SupplyId supplyId;
    private final UserId previousOwnerId;
    private final UserId newOwnerId;
    private final LocalDate effectiveDate;
    private final boolean historyIncluded;
    private final UserId transferredBy;
    private final Instant transferredAt;

    private SupplyOwnershipTransfer(Builder builder) {
        this.id = builder.id;
        this.supplyId = builder.supplyId;
        this.previousOwnerId = builder.previousOwnerId;
        this.newOwnerId = builder.newOwnerId;
        this.effectiveDate = builder.effectiveDate;
        this.historyIncluded = builder.historyIncluded;
        this.transferredBy = builder.transferredBy;
        this.transferredAt = builder.transferredAt;
    }

    public UUID getId() {
        return id;
    }

    public SupplyId getSupplyId() {
        return supplyId;
    }

    /**
     * @return the owner before the transfer, or empty if the supply had no owner
     */
    public Optional<UserId> getPreviousOwnerId() {
        return Optional.ofNullable(previousOwnerId);
    }

    public UserId getNewOwnerId() {
        return newOwnerId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public boolean isHistoryIncluded() {
        return historyIncluded;
    }

    public UserId getTransferredBy() {
        return transferredBy;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public static class Builder {
        private UUID id;
        private SupplyId supplyId;
        private UserId previousOwnerId;
        private UserId newOwnerId;
        private LocalDate effectiveDate;
        private boolean historyIncluded;
        private UserId transferredBy;
        private Instant transferredAt;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withSupplyId(SupplyId supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        /**
         * @param previousOwnerId the owner before the transfer, or {@code null} if the supply had no owner
         */
        public Builder withPreviousOwnerId(UserId previousOwnerId) {
            this.previousOwnerId = previousOwnerId;
            return this;
        }

        public Builder withNewOwnerId(UserId newOwnerId) {
            this.newOwnerId = newOwnerId;
            return this;
        }

        public Builder withEffectiveDate(LocalDate effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder withHistoryIncluded(boolean historyIncluded) {
            this.historyIncluded = historyIncluded;
            return this;
        }

        public Builder withTransferredBy(UserId transferredBy) {
            this.transferredBy = transferredBy;
            return this;
        }

        public Builder withTransferredAt(Instant transferredAt) {
            this.transferredAt = transferredAt;
            return this;
        }

        public SupplyOwnershipTransfer build() {
            return new SupplyOwnershipTransfer(this);
        }
    }
}
