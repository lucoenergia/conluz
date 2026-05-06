package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity(name = "shelly_config")
public class ShellyConfigEntity {

    @Id
    private UUID id;
    private Boolean enabled;

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
}
