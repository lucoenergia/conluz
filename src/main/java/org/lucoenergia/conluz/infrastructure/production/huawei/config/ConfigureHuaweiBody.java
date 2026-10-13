package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationCommand;

import java.util.Optional;

@Schema(requiredProperties = {
        "username", "baseUrl", "enabled"
})
public class ConfigureHuaweiBody {

    @NotBlank
    private String username;
    /**
     * Optional (#399): absent keeps the stored password. An explicit {@code null} fails deserialisation, so
     * a {@code null} here always means the field was absent. {@code @Length} rather than {@code @Size},
     * which would also publish an unbounded {@code maxLength}.
     */
    @Schema(minLength = 1)
    @Length(min = 1)
    @JsonSetter(nulls = Nulls.FAIL)
    private String password;
    @NotBlank
    @URL
    private String baseUrl;
    @NotNull
    private Boolean enabled;

    /**
     * Jackson binds through this constructor and the setters, so an absent property is never set and keeps
     * its {@code null}, while an explicit {@code null} reaches {@link JsonSetter#nulls()}.
     */
    public ConfigureHuaweiBody() {
    }

    public ConfigureHuaweiBody(String username, String password, String baseUrl, Boolean enabled) {
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

    public SetHuaweiConfigurationCommand toCommand() {
        return new SetHuaweiConfigurationCommand(username, Optional.ofNullable(password), baseUrl, enabled);
    }
}
