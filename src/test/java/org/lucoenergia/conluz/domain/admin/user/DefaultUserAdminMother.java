package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;

public class DefaultUserAdminMother {

    public final static String PERSONAL_ID = "01234567Z";
    public final static String PASSWORD = "a secure password!!";
    public final static String FULL_NAME = "Energy Community Acme";
    public final static String ADDRESS = "Fake Street 123";
    public final static String EMAIL = "acmecom@email.com";

    public static DefaultAdminUser get() {
        DefaultAdminUser user = new DefaultAdminUser();
        user.setPersonalId(PERSONAL_ID);
        user.setPassword(PASSWORD);
        user.setFullName(FULL_NAME);
        user.setAddress(ADDRESS);
        user.setEmail(EMAIL);

        return user;
    }
}
