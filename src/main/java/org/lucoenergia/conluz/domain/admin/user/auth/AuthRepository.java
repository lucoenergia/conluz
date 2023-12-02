package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Optional;

public interface AuthRepository {

    Token getToken(User user);

    String getUsernameFromToken(Token token);

    boolean isTokenValid(Token token, User user);
}
