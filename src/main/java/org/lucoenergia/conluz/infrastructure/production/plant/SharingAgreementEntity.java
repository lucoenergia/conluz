package org.lucoenergia.conluz.infrastructure.production.plant;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * No domain object, mapper, service or controller exists for this entity yet -- those arrive in
 * phase 5, once there is a real consumer. The {@code @Schema(description = ...)} comment location
 * required elsewhere in this codebase for user-facing fields cannot be completed until then.
 */
@Entity(name = "sharing_agreement")
public class SharingAgreementEntity {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private PlantEntity plant;
    private String name;
    private String notes;
    @Enumerated(EnumType.STRING)
    private SharingAgreementStatus status;
    @Column(name = "installed_power_kw")
    private BigDecimal installedPowerKw;
    @Column(name = "created_at")
    private Instant createdAt;
    /**
     * NULL means the agreement was created by the system (a migration), not by a person. See the
     * {@code created_by} column remarks in {@code create_table_sharing_agreement.xml} for why no
     * synthetic "system" user is used instead.
     */
    @Column(name = "created_by")
    private UUID createdBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public SharingAgreementStatus getStatus() {
        return status;
    }

    public void setStatus(SharingAgreementStatus status) {
        this.status = status;
    }

    public BigDecimal getInstalledPowerKw() {
        return installedPowerKw;
    }

    public void setInstalledPowerKw(BigDecimal installedPowerKw) {
        this.installedPowerKw = installedPowerKw;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
