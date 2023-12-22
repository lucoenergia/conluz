package org.lucoenergia.conluz.domain.admin.user;

public class DefaultAdminUser extends User {

    private final Integer number;
    private final Role role;

    public DefaultAdminUser() {
        super();
        this.number = 0;
        this.role = Role.ADMIN;
    }

    @Override
    public Integer getNumber() {
        return number;
    }

    @Override
    public Role getRole() {
        return role;
    }
}
