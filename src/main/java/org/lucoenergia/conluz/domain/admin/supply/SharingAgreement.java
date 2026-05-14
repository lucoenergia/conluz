package org.lucoenergia.conluz.domain.admin.supply;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class SharingAgreement {

    private final UUID id;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String notes;
    private final Instant createdAt;
    private final Instant updatedAt;

    private SharingAgreement(Builder builder) {
        this.id = builder.id;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.notes = builder.notes;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static class Builder {
        private UUID id;
        private LocalDate startDate;
        private LocalDate endDate;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder withEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public SharingAgreement build() {
            return new SharingAgreement(this);
        }
    }
}
