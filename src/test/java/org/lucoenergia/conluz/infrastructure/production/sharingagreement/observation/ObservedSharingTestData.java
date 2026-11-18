package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Creates what the observed sharing of a plant reads, the way production creates it: communities, supplies,
 * plants, agreements and coefficients through the domain repositories, and hourly Datadis records through the
 * Influx fixtures. Keeps the codes it wrote records for, so {@link #clear()} removes them.
 */
final class ObservedSharingTestData {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final CreateUserRepository createUserRepository;
    private final CreateCommunityRepository createCommunityRepository;
    private final CreateSupplyRepository createSupplyRepository;
    private final CreatePlantRepository createPlantRepository;
    private final CreateSharingAgreementRepository createSharingAgreementRepository;
    private final SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    private final DatadisConsumptionInfluxFixture consumptionFixture;
    private final DatadisProductionInfluxFixture productionFixture;
    private final List<String> writtenCodes = new ArrayList<>();
    private Instant nextCreatedAt = Instant.parse("2024-01-01T00:00:00Z");

    ObservedSharingTestData(CreateUserRepository createUserRepository,
                            CreateCommunityRepository createCommunityRepository,
                            CreateSupplyRepository createSupplyRepository,
                            CreatePlantRepository createPlantRepository,
                            CreateSharingAgreementRepository createSharingAgreementRepository,
                            SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository,
                            DatadisConsumptionInfluxFixture consumptionFixture,
                            DatadisProductionInfluxFixture productionFixture) {
        this.createUserRepository = createUserRepository;
        this.createCommunityRepository = createCommunityRepository;
        this.createSupplyRepository = createSupplyRepository;
        this.createPlantRepository = createPlantRepository;
        this.createSharingAgreementRepository = createSharingAgreementRepository;
        this.saveSupplyPartitionCoefficientRepository = saveSupplyPartitionCoefficientRepository;
        this.consumptionFixture = consumptionFixture;
        this.productionFixture = productionFixture;
    }

    Community community() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    Supply supply(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    Plant plant(Community community, double peakPowerKWp) {
        Supply supply = supply(community);
        return createPlantRepository.create(PlantMother.random(supply).withPeakPowerKWp(peakPowerKWp).build(),
                SupplyId.of(supply.getId()));
    }

    SharingAgreement agreement(Plant plant, String name, SharingAgreementStatus status, LocalDate sentOn) {
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName(name)
                .withStatus(status)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(nextCreatedAt)
                .withDistributorFileSentOn(sentOn)
                .build());
        nextCreatedAt = nextCreatedAt.plusSeconds(3600);
        return agreement;
    }

    SupplyPartitionCoefficient coefficient(Supply supply, Plant plant, SharingAgreement agreement, String value,
                                           LocalDate validFrom, LocalDate validTo) {
        return saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(new BigDecimal(value))
                .withValidFrom(validFrom == null ? null : validFrom.atStartOfDay(ZONE).toInstant())
                .withValidTo(validTo == null ? null : validTo.atStartOfDay(ZONE).toInstant())
                .withCreatedAt(nextCreatedAt)
                .build());
    }

    /**
     * Every hour of {@code [first, last]}: the plant supply's production, and its consumption record carrying
     * a published, zero self-consumption and a zeroed surplus, as the Datadis sync stores a plant supply.
     */
    void plantProduces(Plant plant, LocalDate first, LocalDate last, float hourlyProductionKWh) {
        String code = plant.getSupply().getCode();
        List<Instant> hours = hours(first, last);
        productionFixture.writeAt(code, hours, hourlyProductionKWh);
        consumptionFixture.writeAt(code, hours, 0f, 0f, 0f);
        writtenCodes.add(code);
    }

    /**
     * Every hour of {@code [first, last]}: the supply receives {@code hourlySelfConsumptionKWh} of the plant's
     * production.
     */
    void supplyReceives(Supply supply, LocalDate first, LocalDate last, float hourlySelfConsumptionKWh) {
        consumptionFixture.writeAt(supply.getCode(), hours(first, last), 0.3f, hourlySelfConsumptionKWh, 0f);
        writtenCodes.add(supply.getCode());
    }

    void clear() {
        writtenCodes.stream().distinct().forEach(code -> {
            consumptionFixture.clear(code);
            productionFixture.clear(code);
        });
        writtenCodes.clear();
    }

    private static List<Instant> hours(LocalDate first, LocalDate last) {
        Instant end = last.plusDays(1).atStartOfDay(ZONE).toInstant();
        return Stream.iterate(first.atStartOfDay(ZONE).toInstant(), time -> time.isBefore(end),
                time -> time.plus(Duration.ofHours(1))).toList();
    }
}
