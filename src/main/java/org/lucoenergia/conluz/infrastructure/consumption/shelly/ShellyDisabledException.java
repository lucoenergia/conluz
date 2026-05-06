package org.lucoenergia.conluz.infrastructure.consumption.shelly;

public class ShellyDisabledException extends RuntimeException {

    public ShellyDisabledException() {
        super("Shelly integration is disabled");
    }
}
