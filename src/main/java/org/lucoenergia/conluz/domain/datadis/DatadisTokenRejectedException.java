package org.lucoenergia.conluz.domain.datadis;

/**
 * Datadis answered a request with 401: the token it carried is no longer accepted.
 */
public class DatadisTokenRejectedException extends DatadisException {

    public DatadisTokenRejectedException(String message) {
        super(message);
    }
}
