package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private CommunityEntity community;

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

    public CommunityEntity getCommunity() {
        return community;
    }

    public void setCommunity(CommunityEntity community) {
        this.community = community;
    }
}
