package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class JwtConfiguration {

    private static final String CONLUZ_JWT_SECRET_KEY = "CONLUZ_JWT_SECRET_KEY";

    @Value("${conluz.security.jwt.expiration-time}")
    private Integer expirationTime;

    @Value("${conluz.security.jwt.secret-key}")
    private String secretKey;

    public Integer getExpirationTime() {
        return expirationTime;
    }

    public String getSecretKey() {
        String secretKeyEnvVarValue = System.getenv(CONLUZ_JWT_SECRET_KEY);
        return StringUtils.hasText(secretKeyEnvVarValue) ? secretKeyEnvVarValue : secretKey;
    }
}
