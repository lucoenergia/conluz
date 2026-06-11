package org.lucoenergia.conluz.domain.admin.user;

public class DefaultAdminUser extends User {

    private final Integer number;

    public DefaultAdminUser() {
        super();
        this.number = 0;
        setPlatformAdmin(true);
    }

    @Override
    public Integer getNumber() {
        return number;
    }
}
