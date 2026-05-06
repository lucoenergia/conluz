package org.lucoenergia.conluz.infrastructure.consumption.datadis;

public class DatadisDisabledException extends RuntimeException {

    public DatadisDisabledException() {
        super("Datadis integration is disabled.");
    }
}
