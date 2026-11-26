package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Creates what a plant's surplus claim reads, the way production creates it: communities, members, supplies and
 * plants through {@link CommunityEnergyTestFixture}, agreements and coefficients through the domain repositories, and
 * hourly Datadis records through the fixture, which removes them on {@link CommunityEnergyTestFixture#clear()}.
 *
 * <p>Communities are created without a time zone of their own, so they are read in the application's, Europe/Madrid.
 */
final class SurplusClaimTestData {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final CommunityEnergyTestFixture fixture;
    private final CreateSharingAgreementRepository createSharingAgreementRepository;
    private final SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    private Instant nextCreatedAt = Instant.parse("2024-01-01T00:00:00Z");

    SurplusClaimTestData(CommunityEnergyTestFixture fixture,
                         CreateSharingAgreementRepository createSharingAgreementRepository,
                         SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository) {
        this.fixture = fixture;
        this.createSharingAgreementRepository = createSharingAgreementRepository;
        this.saveSupplyPartitionCoefficientRepository = saveSupplyPartitionCoefficientRepository;
    }

    /**
     * A plant with a nominal power of 60 kW, on a supply of its own, in a new community with the given number of
     * other supplies, each owned by a member of its own.
     */
    PlantWithSupplies plantWithSupplies(int supplies) {
        return plantWithSupplies(supplies, null);
    }

    /**
     * A plant as {@link #plantWithSupplies(int)} creates it, holding the given CIL, or none when it is null.
     */
    PlantWithSupplies plantWithSupplies(int supplies, String cil) {
        UUID communityId = fixture.createEmptyCommunity().getId();
        Plant plant = fixture.createPlant(communityId, "North", 80.0, 60.0, cil);
        return new PlantWithSupplies(communityId, plant, fixture.createCommunityWithSupplies(communityId, supplies));
    }

    SharingAgreement agreement(Plant plant) {
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + nextCreatedAt)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .withNominalPowerKW(BigDecimal.valueOf(60))
                .withCreatedAt(nextCreatedAt)
                .build());
        nextCreatedAt = nextCreatedAt.plusSeconds(3600);
        return agreement;
    }

    /**
     * A coefficient applied from local midnight of {@code validFrom} until local midnight of {@code validTo}, or
     * pending when {@code validFrom} is null.
     */
    SupplyPartitionCoefficient coefficient(Supply supply, Plant plant, SharingAgreement agreement, String value,
                                           LocalDate validFrom, LocalDate validTo) {
        return saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(new BigDecimal(value))
                .withValidFrom(validFrom == null ? null : validFrom.atStartOfDay(MADRID).toInstant())
                .withValidTo(validTo == null ? null : validTo.atStartOfDay(MADRID).toInstant())
                .withCreatedAt(nextCreatedAt)
                .build());
    }

    /**
     * One hourly record carrying the given surplus, and a self-consumption of 0, at a local hour.
     */
    void surplus(Supply supply, LocalDateTime localHour, float surplusKWh) {
        fixture.writeConsumption(supply, localHour.atZone(MADRID).toInstant(), 0.1f, 0f, surplusKWh);
    }

    /**
     * A record carrying a surplus of 0 at every hour of the month, published: its self-consumption is 0 too.
     */
    void zeroSurplusAllMonth(Supply supply, YearMonth month) {
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(MADRID).toInstant();
        List<Instant> hours = Stream.iterate(month.atDay(1).atStartOfDay(MADRID).toInstant(),
                hour -> hour.isBefore(end), hour -> hour.plus(Duration.ofHours(1))).toList();
        fixture.writeConsumption(supply, hours, 0.1f, 0f, 0f);
    }

    void price(LocalDateTime localTime, double priceKWh) {
        fixture.writePrice(localTime.atZone(MADRID).toInstant(), priceKWh);
    }

    void clear() {
        fixture.clear();
    }

    record PlantWithSupplies(UUID communityId, Plant plant, List<Supply> supplies) {

        Supply first() {
            return supplies.get(0);
        }

        Supply second() {
            return supplies.get(1);
        }
    }
}
