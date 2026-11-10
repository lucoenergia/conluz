package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatadisSupplySelectionTest {

    private static final String CUPS = "ES0031300329693002BQ0F";

    @Test
    @DisplayName("DSS-009 the entry with the most recent contract start date is chosen, whatever the order")
    void theLatestContractIsChosen() {
        DatadisSupply latest = entry("2024/06/01", "2", 5);
        DatadisSupply older = entry("2023/01/01", "1", 5);
        DatadisSupply undated = entry(null, "0", 5);

        for (List<DatadisSupply> order : permutations(List.of(latest, older, undated))) {
            assertThat(DatadisSupplySelection.latest(order)).isSameAs(latest);
        }
    }

    @Test
    @DisplayName("DSS-009 an entry without a start date, or with one that is not a date, ranks after any dated entry")
    void anUndatedEntryRanksLast() {
        DatadisSupply older = entry("2001/01/01", "9", 5);
        DatadisSupply undated = entry(null, "0", 5);
        DatadisSupply unreadable = entry("not a date", "0", 5);

        for (List<DatadisSupply> order : permutations(List.of(undated, unreadable, older))) {
            assertThat(DatadisSupplySelection.latest(order)).isSameAs(older);
        }
    }

    @Test
    @DisplayName("DSS-009 entries with the same start date are chosen by distributor code, then point type, so the "
            + "choice does not depend on the order")
    void aTieIsBrokenByTheWrittenFields() {
        DatadisSupply lowerCode = entry("2024/06/01", "1", 5);
        DatadisSupply higherCode = entry("2024/06/01", "2", 3);
        DatadisSupply lowerPointType = entry("2024/06/01", "1", 4);

        for (List<DatadisSupply> order : permutations(List.of(lowerCode, higherCode, lowerPointType))) {
            assertThat(DatadisSupplySelection.latest(order)).isSameAs(lowerPointType);
        }
    }

    @Test
    @DisplayName("DSS-009 two undated entries are chosen by distributor code, whatever the order")
    void twoUndatedEntriesAreOrderedByTheWrittenFields() {
        DatadisSupply undated = entry(null, "2", 5);
        DatadisSupply unreadable = entry("2024-06-01", "1", 5);

        assertThat(DatadisSupplySelection.latest(List.of(undated, unreadable))).isSameAs(unreadable);
        assertThat(DatadisSupplySelection.latest(List.of(unreadable, undated))).isSameAs(unreadable);
    }

    @Test
    void theStartDateIsReadInTheDatadisFormat() {
        assertThat(DatadisSupplySelection.startDate(entry("2024/06/01", "1", 5))).contains(LocalDate.of(2024, 6, 1));
        assertThat(DatadisSupplySelection.startDate(entry(null, "1", 5))).isEmpty();
        assertThat(DatadisSupplySelection.startDate(entry(" ", "1", 5))).isEmpty();
        assertThat(DatadisSupplySelection.startDate(entry("2024-06-01", "1", 5))).isEmpty();
    }

    private static DatadisSupply entry(String validDateFrom, String distributorCode, int pointType) {
        return new DatadisSupply.Builder()
                .withCups(CUPS)
                .withValidDateFrom(validDateFrom)
                .withDistributorCode(distributorCode)
                .withDistributor("Distributor " + distributorCode)
                .withPointType(pointType)
                .withAddress("Address")
                .build();
    }

    private static List<List<DatadisSupply>> permutations(List<DatadisSupply> entries) {
        if (entries.size() <= 1) {
            return List.of(entries);
        }
        List<List<DatadisSupply>> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            List<DatadisSupply> rest = new ArrayList<>(entries);
            DatadisSupply head = rest.remove(i);
            for (List<DatadisSupply> tail : permutations(rest)) {
                List<DatadisSupply> permutation = new ArrayList<>(Collections.singletonList(head));
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }
}
