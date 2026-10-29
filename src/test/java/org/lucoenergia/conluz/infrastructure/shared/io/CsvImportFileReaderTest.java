package org.lucoenergia.conluz.infrastructure.shared.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvImportFileReaderTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final CsvImportLayout LAYOUT = new CsvImportLayout(List.of("id", "name", "note"),
            List.of("id", "name"), 2);

    private final CsvImportFileReader reader = new CsvImportFileReaderImpl();

    @Test
    @DisplayName("a UTF-8 file with a byte order mark, separated by semicolons, is read with its accents intact")
    void utf8WithBom_isRead() {
        CsvImportFile file = reader.read(withBom("id;name\r\n1;Peña\r\n2;Íñigo\r\n"), LAYOUT);

        assertThat(file.encoding()).isEqualTo(CsvImportEncoding.UTF_8);
        assertThat(file.rows()).extracting(CsvImportRow::line).containsExactly(2, 3);
        assertThat(file.rows()).extracting(CsvImportRow::values)
                .containsExactly(Map.of("id", "1", "name", "Peña"), Map.of("id", "2", "name", "Íñigo"));
    }

    @Test
    @DisplayName("a Windows-1252 file is read as Windows-1252, with its accents intact")
    void windows1252_isRead() {
        CsvImportFile file = reader.read("id;name;note\n1;Begoña;\"cuota, 10 €\"\n".getBytes(
                Charset.forName("windows-1252")), LAYOUT);

        assertThat(file.encoding()).isEqualTo(CsvImportEncoding.WINDOWS_1252);
        assertThat(file.rows().get(0).values()).isEqualTo(Map.of("id", "1", "name", "Begoña",
                "note", "cuota, 10 €"));
    }

    @Test
    @DisplayName("headers are trimmed and may come in any order; cells are kept untrimmed")
    void headersTrimmed_cellsUntrimmed() {
        CsvImportFile file = reader.read(utf8(" name , id \n Ana ,1\n"), LAYOUT);

        assertThat(file.rows().get(0).values()).isEqualTo(Map.of("id", "1", "name", " Ana "));
    }

    @Test
    @DisplayName("blank rows are skipped, rows keep the line they start on, and a row with another number of cells "
            + "is malformed")
    void blankRowsSkipped_malformedRowsKept() {
        CsvImportLayout unbounded = new CsvImportLayout(LAYOUT.columns(), LAYOUT.requiredColumns(), 10);
        CsvImportFile file = reader.read(utf8("id;name\n1;\"An\na\"\n;\n\n2\n3;Luis;extra\n"), unbounded);

        assertThat(file.rows()).extracting(CsvImportRow::line).containsExactly(2, 6, 7);
        assertThat(file.rows()).extracting(CsvImportRow::isMalformed).containsExactly(false, true, true);
    }

    @Test
    @DisplayName("the layout's row limit is accepted, and one more row refuses the file")
    void rowLimit() {
        assertThat(reader.read(utf8("id;name\n1;a\n2;b\n;\n"), LAYOUT).rows()).hasSize(2);
        assertRefused(utf8("id;name\n1;a\n2;b\n3;c\n"), CsvImportFileProblem.TOO_MANY_ROWS, List.of());
    }

    @Test
    @DisplayName("a UTF-16 file, and invalid UTF-8 after a byte order mark, are refused as an invalid encoding")
    void invalidEncoding() {
        assertRefused(new byte[]{(byte) 0xFF, (byte) 0xFE, 'i', 0}, CsvImportFileProblem.INVALID_ENCODING, List.of());
        byte[] invalid = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'i', 'd', (byte) 0xC3};
        assertRefused(invalid, CsvImportFileProblem.INVALID_ENCODING, List.of());
    }

    @Test
    @DisplayName("a header with both separators is refused as ambiguous")
    void ambiguousSeparator() {
        assertRefused(utf8("id;name,note\n"), CsvImportFileProblem.AMBIGUOUS_SEPARATOR, List.of());
    }

    @Test
    @DisplayName("an unclosed quoted value is refused as a malformed file")
    void malformedFile() {
        assertRefused(utf8("id;name\n1;\"Ana\n"), CsvImportFileProblem.MALFORMED_FILE, List.of());
    }

    @Test
    @DisplayName("unknown headers, matched case-sensitively, are refused with their names")
    void unknownHeaders() {
        assertRefused(utf8("id;Name;password\n"), CsvImportFileProblem.UNKNOWN_HEADERS, List.of("Name", "password"));
    }

    @Test
    @DisplayName("duplicated headers are refused with their names")
    void duplicatedHeaders() {
        assertRefused(utf8("id;name;id\n"), CsvImportFileProblem.DUPLICATED_HEADERS, List.of("id"));
    }

    @Test
    @DisplayName("missing required headers are refused with their names, and an empty file lacks all of them")
    void missingHeaders() {
        assertRefused(utf8("name;note\n"), CsvImportFileProblem.MISSING_HEADERS, List.of("id"));
        assertRefused(new byte[0], CsvImportFileProblem.MISSING_HEADERS, List.of("id", "name"));
    }

    private void assertRefused(byte[] content, CsvImportFileProblem problem, List<String> headers) {
        assertThatThrownBy(() -> reader.read(content, LAYOUT))
                .isInstanceOfSatisfying(CsvImportFileRefusedException.class, e -> {
                    assertThat(e.getProblem()).isEqualTo(problem);
                    assertThat(e.getHeaders()).isEqualTo(headers);
                });
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withBom(String text) {
        byte[] body = utf8(text);
        byte[] content = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, content, 0, BOM.length);
        System.arraycopy(body, 0, content, BOM.length, body.length);
        return content;
    }
}
