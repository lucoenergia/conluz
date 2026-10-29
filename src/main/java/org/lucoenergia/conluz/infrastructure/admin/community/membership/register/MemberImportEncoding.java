package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

/**
 * The encodings a file of members is read in: the two that spreadsheets save a CSV file in.
 */
public enum MemberImportEncoding {

    UTF_8("UTF-8"),
    WINDOWS_1252("WINDOWS-1252");

    private final String label;

    MemberImportEncoding(String label) {
        this.label = label;
    }

    /**
     * @return the encoding's name, as it is logged
     */
    public String label() {
        return label;
    }
}
