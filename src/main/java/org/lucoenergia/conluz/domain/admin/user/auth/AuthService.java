package org.lucoenergia.conluz.domain.admin.user.auth;

public interface AuthService {

    Token login(Credentials credentials);

    void logout();
}
