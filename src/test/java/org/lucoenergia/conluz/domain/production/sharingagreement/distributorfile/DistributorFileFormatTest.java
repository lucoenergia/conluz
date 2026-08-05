package org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributorFileFormatTest {

    private static final String CUPS = "ES0031300325733001FH0F";

    @Test
    void normalizeScalePadsFewerThanSixDecimalPlaces() {
        assertEquals(0, new BigDecimal("0.500000").compareTo(DistributorFileFormat.normalizeScale(new BigDecimal("0.5"))));
    }

    @Test
    void normalizeScaleThrowsWhenMoreThanSixDecimalPlaces() {
        assertThrows(ArithmeticException.class,
                () -> DistributorFileFormat.normalizeScale(new BigDecimal("0.1234567")));
    }

    @Test
    void formatCoefficientLineProducesCommaDecimalWithSixDigits() {
        String line = DistributorFileFormat.formatCoefficientLine(CUPS, new BigDecimal("0.333333"));

        assertEquals(CUPS + ";0,333333", line);
    }

    @Test
    void formatCoefficientLinePadsFewerThanSixDecimalPlaces() {
        String line = DistributorFileFormat.formatCoefficientLine(CUPS, new BigDecimal("0.5"));

        assertEquals(CUPS + ";0,500000", line);
    }

    @Test
    void buildFilenameMatchesRegulatoryCodeAndYear() {
        assertEquals("ES0031300325733001FH0FA000_2023.txt",
                DistributorFileFormat.buildFilename("ES0031300325733001FH0FA000", 2023));
    }

    @Test
    void buildFilenameMatchesTheParsersOwnFilenamePattern() {
        String filename = DistributorFileFormat.buildFilename("ES0031300325733001FH0FA000", 2023);

        assertTrue(DistributorFileFormat.FILENAME_PATTERN.matcher(filename).matches());
    }

    @Test
    void isValidSumTrueOnlyForExactlyOne() {
        assertTrue(DistributorFileFormat.isValidSum(new BigDecimal("1.000000")));
        assertTrue(DistributorFileFormat.isValidSum(BigDecimal.ONE));
    }

    @Test
    void isValidSumFalseForNearMisses() {
        assertFalse(DistributorFileFormat.isValidSum(new BigDecimal("0.999999")));
        assertFalse(DistributorFileFormat.isValidSum(new BigDecimal("1.000001")));
        assertFalse(DistributorFileFormat.isValidSum(new BigDecimal("1.00005")));
        assertFalse(DistributorFileFormat.isValidSum(BigDecimal.ZERO));
    }
}
