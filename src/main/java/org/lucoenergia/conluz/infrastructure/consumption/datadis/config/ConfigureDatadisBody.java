package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;

@Schema(requiredProperties = {
        "username", "password"
})
public class ConfigureDatadisBody {

    @NotBlank
    private String username;
    @NotBlank
    private String password;

    public ConfigureDatadisBody(String username, String password) {
        this.username = username;
        this.password = password;
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

    public DatadisConfig toDatadisConfig() {
        return new DatadisConfig(username, password);
    }
}
