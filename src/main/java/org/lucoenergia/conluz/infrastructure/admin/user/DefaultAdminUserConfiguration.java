package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.User;

public interface DefaultAdminUserConfiguration {

    String getDefaultAdminUserId();

    Integer getDefaultAdminUserNumber();

    String getDefaultAdminUserFullName();

    String getDefaultAdminUserAddress();

    String getDefaultAdminUserEmail();

    String getDefaultAdminUserPassword();

    default User getDefaultAdminUser() {
        return new User.Builder()
                .personalId(getDefaultAdminUserId())
                .fullName(getDefaultAdminUserFullName())
                .number(getDefaultAdminUserNumber())
                .email(getDefaultAdminUserEmail())
                .address(getDefaultAdminUserAddress())
                .build();
    }
}
