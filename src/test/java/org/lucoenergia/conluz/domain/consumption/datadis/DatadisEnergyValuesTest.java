package org.lucoenergia.conluz.domain.consumption.datadis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatadisEnergyValuesTest {

    /**
     * Widening 2.37f directly gives 2.3700001239776611; the figure Datadis reported is 2.37.
     */
    @Test
    void readsTheFigureWithoutWideningError() {
        assertEquals(new BigDecimal("2.37"), DatadisEnergyValues.exactlyAsReported(2.37f));
    }

    @Test
    void readsAMissingFigureAsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(DatadisEnergyValues.exactlyAsReported(null)));
    }
}
