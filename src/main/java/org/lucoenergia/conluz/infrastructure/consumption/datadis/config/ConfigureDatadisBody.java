package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;

@Schema(requiredProperties = {
        "username", "password", "baseUrl", "enabled"
})
public class ConfigureDatadisBody {

    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    @URL
    private String baseUrl;
    @NotNull
    private Boolean enabled;

    public ConfigureDatadisBody(String username, String password, String baseUrl, Boolean enabled) {
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
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

    public DatadisConfig toDatadisConfig() {
        return new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl(baseUrl)
                .setEnabled(enabled)
                .build();
    }
}
