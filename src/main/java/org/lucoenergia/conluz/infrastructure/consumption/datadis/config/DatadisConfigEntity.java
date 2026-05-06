package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity(name = "datadis_config")
public class DatadisConfigEntity {

    public static final String CONSUMPTION_KWH_MEASUREMENT = "datadis_consumption_kwh";
    public static final String CONSUMPTION_KWH_MONTH_MEASUREMENT = "datadis_consumption_kwh_month";
    public static final String CONSUMPTION_KWH_YEAR_MEASUREMENT = "datadis_consumption_kwh_year";

    @Id
    private UUID id;
    private String username;
    private String password;
    @Column(name = "base_url")
    private String baseUrl;
    private Boolean enabled;

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
}
