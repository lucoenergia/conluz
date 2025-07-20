package org.lucoenergia.conluz.domain.admin.user.auth;

public interface Authenticator {

    void authenticate(Credentials credentials);
}
