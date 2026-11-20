package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantDailyEnergy;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySource;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Builds the daily series an observation is computed from, in memory: a plant producing 100 kWh every day
 * with every hour published, supplies whose observed β is set day by day, agreements and their rows.
 */
final class ObservationFixture {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final double DEFAULT_PRODUCTION_KWH = 100;

    private final LocalDate from;
    private final LocalDate to;
    private final ObservedSupply plantSupply = new ObservedSupply(UUID.randomUUID(), "PLANT", "Plant supply");
    private double peakPowerKWp = 100;
    private int consecutiveDays = 3;
    private final TreeMap<LocalDate, Double> production = new TreeMap<>();
    private final Map<LocalDate, Long> plantHours = new HashMap<>();
    private final List<ObservedSupply> supplies = new ArrayList<>();
    private final Map<UUID, TreeMap<LocalDate, Double>> betas = new LinkedHashMap<>();
    private final Map<UUID, Map<LocalDate, Long>> supplyHours = new HashMap<>();
    private final List<ObservedAgreement> agreements = new ArrayList<>();
    private final List<ObservedCoefficient> coefficients = new ArrayList<>();
    private final List<ObservedSupply> notComputable = new ArrayList<>();
    private boolean plantWithoutData;
    private Instant nextCreatedAt = Instant.parse("2023-01-01T00:00:00Z");

    ObservationFixture(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            production.put(day, DEFAULT_PRODUCTION_KWH);
        }
    }

    static LocalDate day(String isoDate) {
        return LocalDate.parse(isoDate);
    }

    ObservationFixture peakPowerKWp(double peakPowerKWp) {
        this.peakPowerKWp = peakPowerKWp;
        return this;
    }

    ObservationFixture consecutiveDays(int consecutiveDays) {
        this.consecutiveDays = consecutiveDays;
        return this;
    }

    ObservationFixture plantWithoutData() {
        this.plantWithoutData = true;
        return this;
    }

    ObservedSupply supply(String code) {
        ObservedSupply supply = new ObservedSupply(UUID.randomUUID(), code, "Supply " + code);
        supplies.add(supply);
        betas.put(supply.id(), new TreeMap<>());
        return supply;
    }

    ObservedSupply plantSupply() {
        supplies.add(plantSupply);
        betas.put(plantSupply.id(), new TreeMap<>());
        return plantSupply;
    }

    ObservedSupply notComputableSupply(String code) {
        ObservedSupply supply = new ObservedSupply(UUID.randomUUID(), code, "Supply " + code);
        notComputable.add(supply);
        return supply;
    }

    ObservedAgreement agreement(String name, LocalDate sentOn) {
        return agreement(name, SharingAgreementStatus.PUBLISHED, sentOn);
    }

    ObservedAgreement agreement(String name, SharingAgreementStatus status, LocalDate sentOn) {
        ObservedAgreement agreement = new ObservedAgreement(UUID.randomUUID(), name, status, sentOn, nextCreatedAt);
        nextCreatedAt = nextCreatedAt.plusSeconds(3600);
        agreements.add(agreement);
        return agreement;
    }

    ObservedCoefficient row(ObservedSupply supply, ObservedAgreement agreement, String coefficient,
                            LocalDate validFrom, LocalDate validTo) {
        return rowAt(supply, agreement, coefficient, midnight(validFrom), midnight(validTo));
    }

    ObservedCoefficient rowAt(ObservedSupply supply, ObservedAgreement agreement, String coefficient,
                              Instant validFrom, Instant validTo) {
        ObservedCoefficient row = new ObservedCoefficient(UUID.randomUUID(), supply.id(), agreement.id(),
                new BigDecimal(coefficient), validFrom, validTo);
        coefficients.add(row);
        return row;
    }

    /**
     * The supply receives {@code beta} of the plant's production on every day of {@code [first, last]}.
     */
    ObservationFixture beta(ObservedSupply supply, LocalDate first, LocalDate last, double beta) {
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            betas.get(supply.id()).put(day, beta);
        }
        return this;
    }

    ObservationFixture production(LocalDate day, double productionKWh) {
        production.put(day, productionKWh);
        return this;
    }

    /**
     * The plant supply has no record on any day of {@code [first, last]}.
     */
    ObservationFixture noPlantData(LocalDate first, LocalDate last) {
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            production.remove(day);
        }
        return this;
    }

    ObservationFixture plantPublishedHours(LocalDate day, long hours) {
        plantHours.put(day, hours);
        return this;
    }

    ObservationFixture supplyPublishedHours(ObservedSupply supply, LocalDate day, long hours) {
        supplyHours.computeIfAbsent(supply.id(), id -> new HashMap<>()).put(day, hours);
        return this;
    }

    ObservationInput input() {
        List<PlantDailyEnergy> plantDays = new ArrayList<>();
        production.forEach((day, kWh) ->
                plantDays.add(new PlantDailyEnergy(day, kWh, plantHours.getOrDefault(day, hoursOf(day)),
                        PlantEnergySource.PRODUCTION)));
        Map<UUID, List<SupplyDailyEnergy>> supplyDays = new HashMap<>();
        betas.forEach((supplyId, byDay) -> {
            List<SupplyDailyEnergy> days = new ArrayList<>();
            byDay.forEach((day, beta) -> days.add(new SupplyDailyEnergy(day,
                    beta * production.getOrDefault(day, DEFAULT_PRODUCTION_KWH),
                    supplyHours.getOrDefault(supplyId, Map.of()).getOrDefault(day, hoursOf(day)))));
            supplyDays.put(supplyId, days);
        });
        return new ObservationInput(ZONE, peakPowerKWp, plantSupply,
                plantWithoutData ? null : from, plantWithoutData ? null : to,
                plantWithoutData ? List.of() : plantDays, supplies, supplyDays, notComputable, agreements,
                coefficients);
    }

    PlantSharingObservation calculate() {
        return new ObservedSharingCalculator(consecutiveDays).calculate(input());
    }

    static long hoursOf(LocalDate day) {
        return EnergyMetricsPeriod.ofDay(day, ZONE).getExpectedHours();
    }

    static Instant midnight(LocalDate day) {
        return day == null ? null : day.atStartOfDay(ZONE).toInstant();
    }

    static AgreementObservation agreementOf(PlantSharingObservation result, ObservedAgreement agreement) {
        return result.agreements().stream()
                .filter(observation -> observation.agreement().id().equals(agreement.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Agreement " + agreement.name() + " is not in the result"));
    }

    static CoefficientObservation rowOf(PlantSharingObservation result, ObservedCoefficient row) {
        return result.agreements().stream()
                .flatMap(observation -> observation.coefficients().stream())
                .filter(observation -> observation.coefficientId().equals(row.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Row " + row.id() + " is not in the result"));
    }

    static List<LeavingObservation> leavingsOf(PlantSharingObservation result, ObservedAgreement agreement,
                                               ObservedSupply supply) {
        return agreementOf(result, agreement).leavingRows().stream()
                .filter(row -> row.supply().id().equals(supply.id()))
                .toList();
    }

    static List<SharingAnomaly> anomaliesOf(PlantSharingObservation result, ObservedSupply supply) {
        List<SharingAnomaly> anomalies = new ArrayList<>(result.unattributedAnomalies());
        result.agreements().forEach(observation -> anomalies.addAll(observation.anomalies()));
        return anomalies.stream().filter(anomaly -> anomaly.supply().id().equals(supply.id())).toList();
    }
}
