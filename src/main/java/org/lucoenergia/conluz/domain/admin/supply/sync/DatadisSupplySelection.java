package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Which of the entries Datadis returns for one CUPS, one per contract, a supply is synced from.
 */
public final class DatadisSupplySelection {

    private static final DateTimeFormatter DATADIS_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * The most recent contract start date first, and an entry without one, or with one that cannot be read, last.
     * Entries with the same date are ordered by distributor code, point type, distributor name and address, nulls
     * last: every field the sync writes, so that the entry chosen never depends on the order Datadis returned them.
     */
    private static final Comparator<DatadisSupply> LATEST_FIRST = Comparator
            .comparing((DatadisSupply entry) -> startDate(entry).orElse(null),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(DatadisSupply::getDistributorCode, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DatadisSupply::getPointType, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DatadisSupply::getDistributor, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DatadisSupply::getAddress, Comparator.nullsLast(Comparator.naturalOrder()));

    private DatadisSupplySelection() {
    }

    /**
     * @param entries the entries of one CUPS, not empty
     */
    public static DatadisSupply latest(Collection<DatadisSupply> entries) {
        return entries.stream().min(LATEST_FIRST)
                .orElseThrow(() -> new IllegalArgumentException("No Datadis entry to choose from"));
    }

    /**
     * The contract start date of the entry, or empty when Datadis sent none or one that is not a date.
     */
    public static Optional<LocalDate> startDate(DatadisSupply entry) {
        String date = entry.getValidDateFrom();
        if (date == null || date.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(date, DATADIS_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
