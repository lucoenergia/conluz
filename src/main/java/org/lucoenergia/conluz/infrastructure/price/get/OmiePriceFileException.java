package org.lucoenergia.conluz.infrastructure.price.get;

/**
 * An OMIE price file whose content does not match the format expected for its day.
 */
public class OmiePriceFileException extends RuntimeException {

    public OmiePriceFileException(String message) {
        super(message);
    }
}
