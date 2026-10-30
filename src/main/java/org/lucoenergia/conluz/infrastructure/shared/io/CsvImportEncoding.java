package org.lucoenergia.conluz.infrastructure.shared.io;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The encodings an imported CSV file is read in: the two that spreadsheets save a CSV file in.
 */
public enum CsvImportEncoding {

    UTF_8(StandardCharsets.UTF_8),
    WINDOWS_1252(Charset.forName("windows-1252"));

    private final Charset charset;

    CsvImportEncoding(Charset charset) {
        this.charset = charset;
    }

    Charset charset() {
        return charset;
    }
}
