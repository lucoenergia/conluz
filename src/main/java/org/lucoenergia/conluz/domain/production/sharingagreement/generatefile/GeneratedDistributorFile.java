package org.lucoenergia.conluz.domain.production.sharingagreement.generatefile;

public class GeneratedDistributorFile {

    private final String filename;
    private final byte[] content;

    public GeneratedDistributorFile(String filename, byte[] content) {
        this.filename = filename;
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContent() {
        return content;
    }
}
