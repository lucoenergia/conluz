package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against a real InfluxDB: the half-open bounds, the per-code grouping, and the absence of an entry for
 * a code without records, which a mock could only assume. Each test owns its data under unique CUPS.
 */
class GetDatadisProductionAggregateRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-05-09T22:00:00Z");
    private static final Instant TO = Instant.parse("2026-05-10T22:00:00Z");

    @Autowired
    private GetDatadisProductionAggregateRepository repository;
    @Autowired
    private DatadisProductionInfluxFixture fixture;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clear() {
        writtenCups.forEach(fixture::clear);
    }

    @Test
    void sumsTheProductionOfEachCodeSeparately() {
        String first = cups();
        String second = cups();
        fixture.writeAt(first, FROM, 1.5f);
        fixture.writeAt(first, FROM.plusSeconds(3600), 2.5f);
        fixture.writeAt(second, FROM.plusSeconds(7200), 10f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(first, second), FROM, TO);

        assertEquals(2, sums.size());
        assertEquals(4.0, sums.get(first), 1e-6);
        assertEquals(10.0, sums.get(second), 1e-6);
    }

    @Test
    void countsTheRecordOnTheStartBoundAndNotTheOneOnTheEndBound() {
        String code = cups();
        fixture.writeAt(code, FROM, 1f);
        fixture.writeAt(code, TO, 100f);
        fixture.writeAt(code, FROM.minusSeconds(3600), 1000f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(code), FROM, TO);

        assertEquals(1.0, sums.get(code), 1e-6);
    }

    @Test
    void leavesOutACodeWithoutRecordsAndKeepsAMeasuredZero() {
        String silent = cups();
        String zero = cups();
        fixture.writeAt(zero, FROM, 0f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(silent, zero), FROM, TO);

        assertFalse(sums.containsKey(silent));
        assertEquals(0.0, sums.get(zero), 0.0);
    }

    @Test
    void readsNothingForNoCode() {
        assertTrue(repository.sumProductionByCups(List.of(), FROM, TO).isEmpty());
    }

    private String cups() {
        String code = SupplyMother.randomCups();
        writtenCups.add(code);
        return code;
    }
}
