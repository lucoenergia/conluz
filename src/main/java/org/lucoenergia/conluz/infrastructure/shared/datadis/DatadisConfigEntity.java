package org.lucoenergia.conluz.infrastructure.shared.datadis;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity(name = "datadis_config")
public class DatadisConfigEntity {

    public static final String BASE_URL = "https://datadis.es/api-private/api";
    public static final String CONSUMPTION_KWH_MEASUREMENT = "datadis_consumption_kwh";

    @Id
    private UUID id;
    private String username;
    private String password;

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
}
