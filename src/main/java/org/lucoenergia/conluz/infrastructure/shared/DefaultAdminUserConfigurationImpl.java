package org.lucoenergia.conluz.infrastructure.shared;

import org.lucoenergia.conluz.infrastructure.admin.user.DefaultAdminUserConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("production")
public class DefaultAdminUserConfigurationImpl implements DefaultAdminUserConfiguration {

    @Override
    public String getDefaultAdminUserId() {
        return System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_ID.name());
    }

    @Override
    public Integer getDefaultAdminUserNumber() {
        try {
            return Integer.valueOf(System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_NUMBER.name()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getDefaultAdminUserFullName() {
        return System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_FULL_NAME.name());
    }

    @Override
    public String getDefaultAdminUserAddress() {
        return System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_ADDRESS.name());
    }

    @Override
    public String getDefaultAdminUserEmail() {
        return System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_EMAIL.name());
    }

    @Override
    public String getDefaultAdminUserPassword() {
        return System.getenv(EnvVar.CONLUZ_USER_DEFAULT_ADMIN_PASSWORD.name());
    }
}
