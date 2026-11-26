package org.lucoenergia.conluz.domain.production.plant.surplusclaim.file;

/**
 * The format a plant's surplus claim file is written in.
 */
public enum SurplusClaimFileType {
    /**
     * Endesa's own claim template, with its {@code Carga} sheet filled, inside a ZIP.
     */
    ENDESA,
    /**
     * A CSV matrix of the days and hours, for any retailer.
     */
    GENERIC
}
