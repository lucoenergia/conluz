package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;

import java.util.UUID;

@Entity(name = "shelly_config")
public class ShellyConfigEntity {

    @Id
    private UUID id;
    private Boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private CommunityEntity community;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public CommunityEntity getCommunity() {
        return community;
    }

    public void setCommunity(CommunityEntity community) {
        this.community = community;
    }
}
