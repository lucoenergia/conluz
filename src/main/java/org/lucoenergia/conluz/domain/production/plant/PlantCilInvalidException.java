package org.lucoenergia.conluz.domain.production.plant;

/**
 * A CIL written to a plant is not one: once normalised, it is not exactly 25 ASCII alphanumeric characters (PLT-004).
 * The code is not carried, so that it is never logged or echoed.
 */
public class PlantCilInvalidException extends RuntimeException {
}
