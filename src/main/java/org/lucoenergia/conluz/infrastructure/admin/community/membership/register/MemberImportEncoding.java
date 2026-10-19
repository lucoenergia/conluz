package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The encodings a file of members is read in: the two that spreadsheets save a CSV file in.
 */
public enum MemberImportEncoding {

    UTF_8("UTF-8", StandardCharsets.UTF_8),
    WINDOWS_1252("WINDOWS-1252", Charset.forName("windows-1252"));

    private final String label;
    private final Charset charset;

    MemberImportEncoding(String label, Charset charset) {
        this.label = label;
        this.charset = charset;
    }

    /**
     * @return the encoding's name, as it is logged
     */
    public String label() {
        return label;
    }

    Charset charset() {
        return charset;
    }
}
