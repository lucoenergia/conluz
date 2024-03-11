package org.lucoenergia.conluz.infrastructure.admin.datadis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

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
