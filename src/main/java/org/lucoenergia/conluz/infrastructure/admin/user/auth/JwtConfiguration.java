package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.infrastructure.shared.EnvVar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class JwtConfiguration {

    @Value("${conluz.security.jwt.expiration-time}")
    private Integer expirationTime;

    @Value("${conluz.security.jwt.secret-key}")
    private String secretKey;

    public Integer getExpirationTime() {
        return expirationTime;
    }

    public String getSecretKey() {
        String secretKeyEnvVarValue = System.getenv(EnvVar.CONLUZ_JWT_SECRET_KEY.name());
        return StringUtils.hasText(secretKeyEnvVarValue) ? secretKeyEnvVarValue : secretKey;
    }
}
