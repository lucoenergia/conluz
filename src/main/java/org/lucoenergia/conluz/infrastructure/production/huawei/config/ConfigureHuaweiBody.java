package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

@Schema(requiredProperties = {
        "username", "password"
})
public class ConfigureHuaweiBody {

    @NotBlank
    private String username;
    @NotBlank
    private String password;

    public ConfigureHuaweiBody(String username, String password) {
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

    public HuaweiConfig toHuaweiConfig() {
        return new HuaweiConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .build();
    }
}
