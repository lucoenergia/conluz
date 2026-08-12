package org.lucoenergia.conluz.infrastructure.shared.security.auth;

public class WeakSecretKeyException extends RuntimeException {

    public WeakSecretKeyException(String message, Exception e) {
        super(message, e);
    }
}
