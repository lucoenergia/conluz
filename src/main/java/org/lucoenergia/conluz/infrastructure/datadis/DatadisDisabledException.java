package org.lucoenergia.conluz.infrastructure.datadis;

public class DatadisDisabledException extends RuntimeException {

    public DatadisDisabledException() {
        super("Datadis integration is disabled.");
    }
}
