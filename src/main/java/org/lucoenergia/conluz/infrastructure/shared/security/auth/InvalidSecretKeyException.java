package org.lucoenergia.conluz.infrastructure.shared.security.auth;

public class InvalidSecretKeyException extends RuntimeException {

    public InvalidSecretKeyException(String message, Exception e) {
        super(message, e);
    }
}
