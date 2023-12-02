package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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
        return secretKey;
    }
}
