package org.lucoenergia.conluz.domain.admin.user.auth;

public interface Authenticator {

    public void authenticate(Credentials credentials);
}
