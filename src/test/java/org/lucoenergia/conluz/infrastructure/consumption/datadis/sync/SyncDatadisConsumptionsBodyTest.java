package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncDatadisConsumptionsBodyTest {

    @Test
    void testGetEndDate_WhenYearIsInThePast_ShouldReturnLastDayOfYear() {
        // Arrange
        int year = 2020;
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(year);
        LocalDate expectedEndDate = LocalDate.of(year, 12, 31);

        // Act
        LocalDate actualEndDate = body.getEndDate();

        // Assert
        assertEquals(expectedEndDate, actualEndDate, "The end date should be the last day of the year for past years.");
    }

    @Test
    void testGetEndDate_WhenYearIsInTheFuture_ShouldReturnToday() {
        // Arrange
        int futureYear = LocalDate.now().getYear() + 1;
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(futureYear);
        LocalDate today = LocalDate.now();

        // Act
        LocalDate actualEndDate = body.getEndDate();

        // Assert
        assertEquals(today, actualEndDate, "The end date should be today for future years.");
    }

    @Test
    void testGetEndDate_WhenYearIsCurrentYearAndLastDayHasPassed_ShouldReturnLastDayOfYear() {
        // Arrange
        int currentYear = LocalDate.now().getYear();
        SyncDatadisConsumptionsBody body = new SyncDatadisConsumptionsBody(currentYear);
        LocalDate today = LocalDate.now();

        // Act
        LocalDate actualEndDate = body.getEndDate();

        // Assert
        assertEquals(today, actualEndDate, "The end date should be today for future years.");
    }
}