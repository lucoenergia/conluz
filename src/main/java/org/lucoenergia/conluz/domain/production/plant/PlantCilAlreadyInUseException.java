package org.lucoenergia.conluz.domain.production.plant;

/**
 * Another plant already holds the CIL, so it is not given to a second one (PLT-005). The code is not carried, so that
 * it is never logged or echoed.
 */
public class PlantCilAlreadyInUseException extends RuntimeException {

    public PlantCilAlreadyInUseException() {
    }

    public PlantCilAlreadyInUseException(Throwable cause) {
        super(cause);
    }
}
