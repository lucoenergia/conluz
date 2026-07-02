package org.lucoenergia.conluz.domain.datadis;

public class DatadisException extends RuntimeException {

    public DatadisException(String message) {
        super(message);
    }

    public DatadisException(String message, Throwable cause) {
        super(message, cause);
    }
}
