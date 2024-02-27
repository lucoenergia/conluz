package org.lucoenergia.conluz.infrastructure.shared.datadis;

public class DatadisException extends RuntimeException {

    public DatadisException(String message) {
        super(message);
    }

    public DatadisException(String message, Throwable cause) {
        super(message, cause);
    }
}
