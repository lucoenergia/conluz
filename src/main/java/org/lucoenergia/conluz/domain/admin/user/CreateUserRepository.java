package org.lucoenergia.conluz.domain.admin.user;

public interface CreateUserRepository {

    User create (User user, String password);
}
