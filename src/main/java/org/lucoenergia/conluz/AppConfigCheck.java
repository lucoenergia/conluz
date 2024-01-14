package org.lucoenergia.conluz;

import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AppConfigCheck implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigCheck.class);

    private final JwtConfiguration jwtConfiguration;

    public AppConfigCheck(JwtConfiguration jwtConfiguration) {
        this.jwtConfiguration = jwtConfiguration;
    }

    @Override
    public void run(ApplicationArguments args) {
        isSecretKeyPresent();
    }

    private void isSecretKeyPresent() {
        // Secret key must be present
        String secretKey = jwtConfiguration.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            LOGGER.error("Secret key not found. You must provide a secret key using the parameter {}",
                    JwtConfiguration.CONLUZ_JWT_SECRET_KEY);
            System.exit(1);
        }
    }
}

