package org.lucoenergia.conluz.infrastructure.admin.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity(name = "config")
public class ConfigEntity {

    @Id
    private UUID id;
    private boolean defaultAdminUserInitialized;

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public boolean isDefaultAdminUserInitialized() {
        return defaultAdminUserInitialized;
    }

    public void setDefaultAdminUserInitialized(boolean defaultAdminUserInitialized) {
        this.defaultAdminUserInitialized = defaultAdminUserInitialized;
    }

    public void markDefaultAdminUserAsInitialized() {
        setDefaultAdminUserInitialized(true);
    }
}
