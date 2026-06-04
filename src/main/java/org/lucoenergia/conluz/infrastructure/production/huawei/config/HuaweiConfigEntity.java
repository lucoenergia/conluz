package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;

import java.util.UUID;

@Entity(name = "huawei_config")
public class HuaweiConfigEntity {

    @Id
    private UUID id;
    private String username;
    private String password;
    @Column(name = "base_url")
    private String baseUrl;
    private Boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private PlantEntity plant;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }
}
