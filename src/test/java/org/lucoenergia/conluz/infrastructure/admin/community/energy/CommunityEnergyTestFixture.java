package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the communities the community energy tests read, through the domain repositories, and writes
 * and clears their Datadis records.
 *
 * <p>{@link #createCommunity()} builds a community with two plants, "North" and "South", whose own
 * supplies member A owns, and three consumer supplies owned by three different members, the third of
 * whom has a disabled membership. A fourth consumer supply is disabled, so it is out of scope.
 *
 * <p>OMIE prices are written only inside {@link #PRICES_FROM} and {@link #PRICES_TO}, a window no other
 * test writes prices to, since prices are not kept per community and {@link #clear()} deletes the whole
 * window.
 */
@Profile("test")
@Component
public class CommunityEnergyTestFixture {

    public static final Instant PRICES_FROM = Instant.parse("2025-09-10T00:00:00Z");
    public static final Instant PRICES_TO = Instant.parse("2025-10-20T00:00:00Z");

    private final CreateCommunityRepository createCommunityRepository;
    private final CreateUserRepository createUserRepository;
    private final CreateMembershipRepository createMembershipRepository;
    private final SetMembershipEnabledRepository setMembershipEnabledRepository;
    private final CreateSupplyRepository createSupplyRepository;
    private final DisableSupplyRepository disableSupplyRepository;
    private final CreatePlantRepository createPlantRepository;
    private final DatadisConsumptionInfluxFixture consumptionFixture;
    private final DatadisProductionInfluxFixture productionFixture;
    private final PersistOmiePricesRepository persistOmiePricesRepository;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    private final List<String> writtenCups = new ArrayList<>();
    private boolean pricesWritten;

    public CommunityEnergyTestFixture(CreateCommunityRepository createCommunityRepository,
                                      CreateUserRepository createUserRepository,
                                      CreateMembershipRepository createMembershipRepository,
                                      SetMembershipEnabledRepository setMembershipEnabledRepository,
                                      CreateSupplyRepository createSupplyRepository,
                                      DisableSupplyRepository disableSupplyRepository,
                                      CreatePlantRepository createPlantRepository,
                                      DatadisConsumptionInfluxFixture consumptionFixture,
                                      DatadisProductionInfluxFixture productionFixture,
                                      PersistOmiePricesRepository persistOmiePricesRepository,
                                      InfluxDbConnectionManager influxDbConnectionManager) {
        this.createCommunityRepository = createCommunityRepository;
        this.createUserRepository = createUserRepository;
        this.createMembershipRepository = createMembershipRepository;
        this.setMembershipEnabledRepository = setMembershipEnabledRepository;
        this.createSupplyRepository = createSupplyRepository;
        this.disableSupplyRepository = disableSupplyRepository;
        this.createPlantRepository = createPlantRepository;
        this.consumptionFixture = consumptionFixture;
        this.productionFixture = productionFixture;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    /**
     * A community, its members, its supplies and its plants.
     *
     * @param members  members A, B and C; C's membership is disabled
     * @param supplies the consumer supplies of A, B and C, in that order
     */
    public record EnergyCommunity(UUID id, List<User> members, Plant north, Plant south, List<Supply> supplies,
                                  Supply disabledSupply) {

        public User memberA() {
            return members.get(0);
        }

        public User disabledMember() {
            return members.get(2);
        }

        /** Every supply of the community, in scope or not, plant supplies included. */
        public List<Supply> allSupplies() {
            List<Supply> all = new ArrayList<>(supplies);
            all.add(north.getSupply());
            all.add(south.getSupply());
            all.add(disabledSupply);
            return all;
        }
    }

    public EnergyCommunity createCommunity() {
        UUID communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        User a = createMember(communityId);
        User b = createMember(communityId);
        User c = createMember(communityId);
        setMembershipEnabledRepository.setEnabled(communityId, UserId.of(c.getId()), CommunityRole.COMMUNITY_MEMBER,
                false);

        Plant north = createPlant(createSupply(a, communityId), "North", 100.0, 80.0);
        Plant south = createPlant(createSupply(a, communityId), "South", 50.5, 50.5);
        List<Supply> supplies = List.of(createSupply(a, communityId), createSupply(b, communityId),
                createSupply(c, communityId));
        Supply disabled = createSupply(b, communityId);
        disableSupplyRepository.disable(SupplyId.of(disabled.getId()));

        return new EnergyCommunity(communityId, List.of(a, b, c), north, south, supplies, disabled);
    }

    /**
     * A community without plants whose given number of members own one supply each.
     */
    public List<Supply> createCommunityWithSupplies(UUID communityId, int count) {
        List<Supply> supplies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            supplies.add(createSupply(createMember(communityId), communityId));
        }
        return supplies;
    }

    public Community createEmptyCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    public User createMember(UUID communityId) {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    /**
     * One hourly consumption record. A null energy value is stored as an absent field.
     */
    public void writeConsumption(Supply supply, Instant time, Float gridImportKWh, Float selfConsumptionKWh,
                                 Float surplusKWh) {
        writeConsumption(supply, List.of(time), gridImportKWh, selfConsumptionKWh, surplusKWh);
    }

    /**
     * One hourly consumption record at each instant, all with the same values.
     */
    public void writeConsumption(Supply supply, List<Instant> times, Float gridImportKWh, Float selfConsumptionKWh,
                                 Float surplusKWh) {
        consumptionFixture.writeAt(supply.getCode(), times, gridImportKWh, selfConsumptionKWh, surplusKWh);
        track(supply.getCode());
    }

    public void writeProduction(Plant plant, Instant time, float productionKWh) {
        productionFixture.writeAt(plant.getSupply().getCode(), time, productionKWh);
        track(plant.getSupply().getCode());
    }

    /**
     * One OMIE price, in €/kWh, at the given instant, which must fall inside {@link #PRICES_FROM} and
     * {@link #PRICES_TO}.
     */
    public void writePrice(Instant time, double priceKWh) {
        if (time.isBefore(PRICES_FROM) || !time.isBefore(PRICES_TO)) {
            throw new IllegalArgumentException("A price outside the fixture's window: " + time);
        }
        persistOmiePricesRepository.persistPrices(List.of(new PriceByHour(priceKWh, time.atOffset(ZoneOffset.UTC))));
        pricesWritten = true;
    }

    /**
     * Removes every record written since the last call, and every price in the fixture's window.
     */
    public void clear() {
        writtenCups.forEach(cups -> {
            consumptionFixture.clear(cups);
            consumptionFixture.clearMonthlyAggregates(cups);
            productionFixture.clear(cups);
        });
        writtenCups.clear();
        if (pricesWritten) {
            // No port deletes prices: production never does. The price sync tests delete theirs the same way.
            try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
                connection.query(new Query(String.format("DELETE FROM \"%s\" WHERE time >= '%s' AND time < '%s'",
                        OmieConfig.PRICES_KWH_MEASUREMENT, PRICES_FROM, PRICES_TO)));
            }
            pricesWritten = false;
        }
    }

    private Supply createSupply(User owner, UUID communityId) {
        return createSupplyRepository.create(SupplyMother.random().withEnabled(true).build(),
                UserId.of(owner.getId()), communityId);
    }

    /**
     * A plant of the community, on a supply of its own owned by a new member.
     */
    public Plant createPlant(UUID communityId, String name, double peakPowerKWp, double nominalPowerKW) {
        return createPlant(createSupply(createMember(communityId), communityId), name, peakPowerKWp, nominalPowerKW);
    }

    private Plant createPlant(Supply supply, String name, double peakPowerKWp, double nominalPowerKW) {
        return createPlantRepository.create(PlantMother.random(supply).withName(name).withPeakPowerKWp(peakPowerKWp)
                .withNominalPowerKW(nominalPowerKW).build(), SupplyId.of(supply.getId()));
    }

    private void track(String cups) {
        if (!writtenCups.contains(cups)) {
            writtenCups.add(cups);
        }
    }
}
