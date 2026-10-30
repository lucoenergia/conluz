package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileProblem;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileReaderImpl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberImportCsvReaderTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String WINDOWS_1252_TEXT = "personalId;fullName;address\r\n"
            + "12345678Z;Begoña Íñiguez Güell;\"Plaza de la Peña, cuota 10 €\"\r\n";
    private static final Map<String, String> WINDOWS_1252_ROW = Map.of("personalId", "12345678Z",
            "fullName", "Begoña Íñiguez Güell", "address", "Plaza de la Peña, cuota 10 €");

    private final MemberImportCsvReader reader = new MemberImportCsvReader(new CsvImportFileReaderImpl());

    @Test
    @DisplayName("MEM-030 a semicolon-separated file with a byte order mark is read with its accents intact")
    void aSemicolonFileWithABom_isRead() {
        byte[] content = withBom("personalId;fullName;address\r\n12345678Z;Begoña Íñiguez;Plaza de la Peña\r\n");

        MemberImportCsv file = reader.read(content);
        List<MemberImportCsvRow> rows = file.rows();

        assertThat(file.encoding()).isEqualTo(MemberImportEncoding.UTF_8);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.line()).isEqualTo(2);
            assertThat(row.values()).isEqualTo(Map.of("personalId", "12345678Z", "fullName", "Begoña Íñiguez",
                    "address", "Plaza de la Peña"));
        });
    }

    @Test
    @DisplayName("MEM-030 a comma-separated file without a byte order mark is read the same way")
    void aCommaFileWithoutABom_isRead() {
        List<MemberImportCsvRow> rows = reader.read(utf8("personalId,fullName,address\n12345678Z,Begoña Íñiguez,"
                + "\"Plaza de la Peña, 3\"\n")).rows();

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.values()).isEqualTo(Map.of(
                "personalId", "12345678Z", "fullName", "Begoña Íñiguez", "address", "Plaza de la Peña, 3")));
    }

    @Test
    @DisplayName("MEM-030 cells are kept untrimmed and a backslash is an ordinary character")
    void cells_areKeptAsTheyAre() {
        List<MemberImportCsvRow> rows = reader.read(utf8("personalId;fullName;address\n 1 ;  Ana  ;C\\ Mayor\n"))
                .rows();

        assertThat(rows.get(0).values()).isEqualTo(Map.of("personalId", " 1 ", "fullName", "  Ana  ",
                "address", "C\\ Mayor"));
    }

    @Test
    @DisplayName("MEM-030 headers are trimmed and may come in any order, and the optional ones may be left out")
    void headers_areTrimmed_inAnyOrder() {
        List<MemberImportCsvRow> rows = reader.read(utf8(" fullName ; personalId \nAna;1\n")).rows();

        assertThat(rows.get(0).values()).isEqualTo(Map.of("personalId", "1", "fullName", "Ana"));
    }

    @Test
    @DisplayName("MEM-030 a row starting after a quoted value over two lines is numbered by the line it starts on")
    void lineNumbers_followTheFile() {
        List<MemberImportCsvRow> rows = reader.read(utf8("personalId;fullName;address\n1;Ana;\"Calle\nMayor\"\n"
                + "\n2;Luis;Plaza\n")).rows();

        assertThat(rows).extracting(MemberImportCsvRow::line).containsExactly(2, 5);
        assertThat(rows.get(0).values().get("address")).isEqualTo("Calle\nMayor");
    }

    @Test
    @DisplayName("MEM-030 rows whose cells are all blank are skipped")
    void blankRows_areSkipped() {
        List<MemberImportCsvRow> rows = reader.read(utf8("personalId;fullName\n1;Ana\n;\n \t; \n\n2;Luis\n;\n")).rows();

        assertThat(rows).extracting(MemberImportCsvRow::line).containsExactly(2, 6);
    }

    @Test
    @DisplayName("MEM-030 a row without as many cells as the header is malformed, and the other rows are read")
    void aRowWithTheWrongNumberOfCells_isMalformed() {
        List<MemberImportCsvRow> rows = reader.read(utf8("personalId;fullName\n1;Ana;extra\n2\n3;Luis\n")).rows();

        assertThat(rows).extracting(MemberImportCsvRow::isMalformed).containsExactly(true, true, false);
        assertThat(rows).extracting(MemberImportCsvRow::line).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("MEM-030 a file of exactly 1000 data rows is read")
    void aFileOf1000Rows_isRead() {
        assertThat(reader.read(fileOf(1000)).rows()).hasSize(1000);
    }

    @Test
    @DisplayName("MEM-030 a file of 1001 data rows is refused")
    void aFileOf1001Rows_isRefused() {
        assertRefused(fileOf(1001), MemberImportFileProblem.TOO_MANY_ROWS, List.of());
    }

    @Test
    @DisplayName("MEM-030 blank rows do not count towards the limit")
    void blankRows_doNotCount() {
        String content = new String(fileOf(1000), StandardCharsets.UTF_8) + ";\n;\n";

        assertThat(reader.read(utf8(content)).rows()).hasSize(1000);
    }

    // --- Encoding ---

    @Test
    @DisplayName("MEM-030 a file as Excel saves CSV on Windows, in Windows-1252 and without a byte order mark, is read "
            + "with every character intact")
    void aWindows1252File_isRead() {
        MemberImportCsv file = reader.read(WINDOWS_1252_TEXT.getBytes(WINDOWS_1252));

        assertThat(file.encoding()).isEqualTo(MemberImportEncoding.WINDOWS_1252);
        assertThat(file.rows()).singleElement().satisfies(row -> assertThat(row.values()).isEqualTo(WINDOWS_1252_ROW));
    }

    @Test
    @DisplayName("MEM-030 a UTF-8 file without a byte order mark, with the same characters, is read as UTF-8")
    void aUtf8FileWithoutABom_isReadAsUtf8() {
        MemberImportCsv file = reader.read(utf8(WINDOWS_1252_TEXT));

        assertThat(file.encoding()).isEqualTo(MemberImportEncoding.UTF_8);
        assertThat(file.rows()).singleElement().satisfies(row -> assertThat(row.values()).isEqualTo(WINDOWS_1252_ROW));
    }

    @Test
    @DisplayName("MEM-030 a UTF-16 file with its byte order mark, as Excel saves Unicode text, is refused")
    void aUtf16File_isRefused() {
        String text = "personalId;fullName\r\n1;Peña\r\n";

        assertRefused(withPrefix(new byte[]{(byte) 0xFF, (byte) 0xFE}, text.getBytes(StandardCharsets.UTF_16LE)),
                MemberImportFileProblem.INVALID_ENCODING, List.of());
        assertRefused(withPrefix(new byte[]{(byte) 0xFE, (byte) 0xFF}, text.getBytes(StandardCharsets.UTF_16BE)),
                MemberImportFileProblem.INVALID_ENCODING, List.of());
    }

    @Test
    @DisplayName("MEM-030 a file that is not valid UTF-8 and holds a byte Windows-1252 does not define is refused")
    void anUndefinedWindows1252Byte_isRefused() {
        for (int undefined : new int[]{0x81, 0x8D, 0x8F, 0x90, 0x9D}) {
            // "Peña" in Windows-1252, which is not valid UTF-8, followed by the undefined byte
            byte[] content = withPrefix(utf8("personalId;fullName\n1;Pe"),
                    new byte[]{(byte) 0xF1, 'a', (byte) undefined, '\n'});

            assertRefused(content, MemberImportFileProblem.INVALID_ENCODING, List.of());
        }
    }

    @Test
    @DisplayName("MEM-030 a file with a UTF-8 byte order mark that is not valid UTF-8 is refused, not read as "
            + "Windows-1252")
    void invalidUtf8AfterABom_isRefused() {
        byte[] content = withBom("personalId;fullName\n1;Peña\n");
        content[content.length - 4] = (byte) 0xF1;

        assertRefused(content, MemberImportFileProblem.INVALID_ENCODING, List.of());
    }

    @Test
    @DisplayName("MEM-030 a header line with both separators is refused")
    void bothSeparators_areRefused() {
        assertRefused(utf8("personalId;fullName,address\n1;Ana,Calle\n"), MemberImportFileProblem.AMBIGUOUS_SEPARATOR,
                List.of());
    }

    @Test
    @DisplayName("MEM-030 a quoted value that is never closed is refused")
    void anUnclosedQuote_isRefused() {
        assertRefused(utf8("personalId;fullName\n1;\"Ana\n2;Luis\n"), MemberImportFileProblem.MALFORMED_FILE,
                List.of());
    }

    @Test
    @DisplayName("MEM-030 unknown headers, such as the old import's password and role columns, are refused by name")
    void unknownHeaders_areRefused() {
        assertRefused(utf8("personalId;fullName;password;role;\n1;Ana;secret;PARTNER;\n"),
                MemberImportFileProblem.UNKNOWN_HEADERS, List.of("password", "role", ""));
    }

    @Test
    @DisplayName("MEM-030 the issue's number and phone columns are unknown: the columns are the registration's fields")
    void numberAndPhone_areUnknown() {
        assertRefused(utf8("personalId;fullName;number;phone\n1;Ana;7;600\n"), MemberImportFileProblem.UNKNOWN_HEADERS,
                List.of("number", "phone"));
    }

    @Test
    @DisplayName("MEM-030 headers are matched with their case")
    void headers_areCaseSensitive() {
        assertRefused(utf8("PersonalId;fullName\n1;Ana\n"), MemberImportFileProblem.UNKNOWN_HEADERS,
                List.of("PersonalId"));
    }

    @Test
    @DisplayName("MEM-030 a header named twice is refused by name")
    void duplicatedHeaders_areRefused() {
        assertRefused(utf8("personalId;fullName;address; address\n1;Ana;A;B\n"),
                MemberImportFileProblem.DUPLICATED_HEADERS, List.of("address"));
    }

    @Test
    @DisplayName("MEM-030 a missing required header is refused by name")
    void aMissingRequiredHeader_isRefused() {
        assertRefused(utf8("personalId;address\n1;Calle\n"), MemberImportFileProblem.MISSING_HEADERS,
                List.of("fullName"));
    }

    @Test
    @DisplayName("MEM-030 an empty file, or one holding only a byte order mark, lacks every required header")
    void anEmptyFile_isRefused() {
        assertRefused(new byte[0], MemberImportFileProblem.MISSING_HEADERS, List.of("personalId", "fullName"));
        assertRefused(BOM, MemberImportFileProblem.MISSING_HEADERS, List.of("personalId", "fullName"));
    }

    @Test
    @DisplayName("MEM-030 unknown headers are reported before duplicated and missing ones")
    void unknownHeaders_comeFirst() {
        assertRefused(utf8("personalId;personalId;password\n1;1;x\n"), MemberImportFileProblem.UNKNOWN_HEADERS,
                List.of("password"));
    }

    // --- Helpers ---

    private void assertRefused(byte[] content, MemberImportFileProblem problem, List<String> headers) {
        assertThatThrownBy(() -> reader.read(content))
                .isInstanceOfSatisfying(MemberImportFileRefusedException.class, e -> {
                    assertThat(e.getProblem()).isEqualTo(problem);
                    assertThat(e.getHeaders()).isEqualTo(headers);
                });
    }

    private static byte[] fileOf(int rows) {
        StringBuilder content = new StringBuilder("personalId;fullName\n");
        for (int i = 0; i < rows; i++) {
            content.append(i).append(";Member ").append(i).append('\n');
        }
        return utf8(content.toString());
    }

    private static byte[] utf8(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withBom(String content) {
        return withPrefix(BOM, utf8(content));
    }

    private static byte[] withPrefix(byte[] prefix, byte[] content) {
        byte[] result = new byte[prefix.length + content.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        return result;
    }
}
