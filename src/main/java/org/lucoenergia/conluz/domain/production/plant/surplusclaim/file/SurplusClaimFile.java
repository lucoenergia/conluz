package org.lucoenergia.conluz.domain.production.plant.surplusclaim.file;

/**
 * A plant's surplus claim written as a file, ready to be downloaded.
 *
 * @param filename    the name the file is downloaded with
 * @param contentType the media type of the content
 * @param content     the bytes of the file
 */
public record SurplusClaimFile(String filename, String contentType, byte[] content) {
}
