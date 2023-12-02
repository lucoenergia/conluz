package org.lucoenergia.conluz.infrastructure.shared;

import org.lucoenergia.conluz.infrastructure.admin.user.DefaultAdminUserConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class DefaultAdminUserTestConfiguration implements DefaultAdminUserConfiguration {

    public final static String DEFAULT_ADMIN_ID = "01234567Z";
    public final static String DEFAULT_ADMIN_PASSWORD = "a secure password!!";

    @Override
    public String getDefaultAdminUserId() {
        return DEFAULT_ADMIN_ID;
    }

    @Override
    public Integer getDefaultAdminUserNumber() {
        return 0;
    }

    @Override
    public String getDefaultAdminUserFullName() {
        return "Energy Community Acme";
    }

    @Override
    public String getDefaultAdminUserAddress() {
        return "Fake Street 123";
    }

    @Override
    public String getDefaultAdminUserEmail() {
        return "acmecom@email.com";
    }

    @Override
    public String getDefaultAdminUserPassword() {
        return DEFAULT_ADMIN_PASSWORD;
    }
}
