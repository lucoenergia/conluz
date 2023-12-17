package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Optional;

public interface DefaultAdminUserConfiguration {

    String getDefaultAdminUserId();

    Integer getDefaultAdminUserNumber();

    String getDefaultAdminUserFullName();

    String getDefaultAdminUserAddress();

    String getDefaultAdminUserEmail();

    String getDefaultAdminUserPassword();

    default Optional<User> getDefaultAdminUser() {
        if (isConfigurationSet()) {
            return Optional.of(new User.Builder()
                    .personalId(getDefaultAdminUserId())
                    .password(getDefaultAdminUserPassword())
                    .fullName(getDefaultAdminUserFullName())
                    .number(getDefaultAdminUserNumber())
                    .email(getDefaultAdminUserEmail())
                    .address(getDefaultAdminUserAddress())
                    .build());
        }
        return Optional.empty();
    }

    default boolean isConfigurationSet() {
        return getDefaultAdminUserId() != null && getDefaultAdminUserFullName() != null &&
                getDefaultAdminUserNumber()  != null && getDefaultAdminUserEmail() != null &&
                getDefaultAdminUserAddress()  != null;
    }
}
