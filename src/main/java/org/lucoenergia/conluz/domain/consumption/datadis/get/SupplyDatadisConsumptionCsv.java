package org.lucoenergia.conluz.domain.consumption.datadis.get;

/**
 * A supply's hourly Datadis records of one month written as a CSV file, ready to be downloaded.
 *
 * @param filename the name the file is downloaded with
 * @param content  the bytes of the file
 */
public record SupplyDatadisConsumptionCsv(String filename, byte[] content) {
}
