package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class CreateSupplyRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldStoreTheSupplyWithItsOwnerCommunityAndDetails() {
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();

        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM supplies WHERE id = ?", result.getId());
        assertEquals(supply.getCode(), row.get("code"));
        assertEquals(supply.getName(), row.get("name"));
        assertEquals(supply.getAddress(), row.get("address"));
        assertEquals(supply.getAddressRef(), row.get("address_ref"));
        assertEquals(supply.getEnabled(), row.get("enabled"));
        assertEquals(user.getId(), row.get("user_id"));
        assertEquals(DEFAULT_COMMUNITY_ID, row.get("community_id"));

        Map<String, Object> shelly = jdbcTemplate.queryForMap("SELECT * FROM supplies_shelly WHERE supply_id = ?",
                result.getId());
        assertEquals(supply.getShelly().getId(), shelly.get("id"));
        assertEquals(supply.getShelly().getMacAddress(), shelly.get("mac_address"));
        assertEquals(supply.getShelly().getMqttPrefix(), shelly.get("mqtt_prefix"));
        Map<String, Object> distributor = jdbcTemplate.queryForMap(
                "SELECT * FROM supplies_distributor WHERE supply_id = ?", result.getId());
        assertEquals(supply.getDistributor().getName(), distributor.get("name"));
        assertEquals(supply.getDistributor().getCode(), distributor.get("code"));
        assertEquals(supply.getDistributor().getPointType(), distributor.get("point_type"));
        Map<String, Object> contract = jdbcTemplate.queryForMap("SELECT * FROM supplies_contract WHERE supply_id = ?",
                result.getId());
        assertEquals(Date.valueOf(supply.getContract().getValidDateFrom()), contract.get("valid_date_from"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM supplies WHERE user_id = ?", Integer.class,
                user.getId()));
    }


    @Test
    void shouldCreateSupplyWithAllOneToOneRelations() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertEquals(supply.getName(), result.getName());
        assertEquals(supply.getAddress(), result.getAddress());
        assertEquals(supply.getAddressRef(), result.getAddressRef());
        assertEquals(supply.getEnabled(), result.getEnabled());
        assertEquals(user.getId(), result.getUser().getId());

        assertNotNull(result.getShelly());
        assertEquals(supply.getShelly().getId(), result.getShelly().getId());
        assertEquals(supply.getShelly().getMacAddress(), result.getShelly().getMacAddress());
        assertEquals(supply.getShelly().getMqttPrefix(), result.getShelly().getMqttPrefix());

        assertNotNull(result.getDistributor());
        assertEquals(supply.getDistributor().getName(), result.getDistributor().getName());
        assertEquals(supply.getDistributor().getCode(), result.getDistributor().getCode());
        assertEquals(supply.getDistributor().getPointType(), result.getDistributor().getPointType());

        assertNotNull(result.getContract());
        assertEquals(supply.getContract().getValidDateFrom(), result.getContract().getValidDateFrom());
    }

    @Test
    void shouldCreateSupplyWithoutOptionalRelations() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user)
                .withShelly(null)
                .withDistributor(null)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertNull(result.getShelly());
        assertNull(result.getDistributor());
        assertNull(result.getContract());
    }

    @Test
    void shouldCreateSupplyWithOnlyShelly() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        SupplyShelly shelly = new SupplyShelly.Builder()
                .withId("shelly-id-01")
                .withMacAddress("AA:BB:CC:DD:EE:FF")
                .withMqttPrefix("shellyplus1pm")
                .build();
        Supply supply = SupplyMother.random(user)
                .withShelly(shelly)
                .withDistributor(null)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNotNull(result.getShelly());
        assertEquals("shelly-id-01", result.getShelly().getId());
        assertEquals("AA:BB:CC:DD:EE:FF", result.getShelly().getMacAddress());
        assertEquals("shellyplus1pm", result.getShelly().getMqttPrefix());
        assertNull(result.getDistributor());
        assertNull(result.getContract());
    }

    @Test
    void shouldCreateSupplyWithOnlyDistributor() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        SupplyDistributor distributor = new SupplyDistributor.Builder()
                .withName("EDISTRIBUCION")
                .withCode("2")
                .withPointType(5)
                .build();
        Supply supply = SupplyMother.random(user)
                .withShelly(null)
                .withDistributor(distributor)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNull(result.getShelly());
        assertNotNull(result.getDistributor());
        assertEquals("EDISTRIBUCION", result.getDistributor().getName());
        assertEquals("2", result.getDistributor().getCode());
        assertEquals(5, result.getDistributor().getPointType());
        assertNull(result.getContract());
    }

    @Test
    void shouldCreateSupplyWithOnlyContract() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        LocalDate validDateFrom = LocalDate.of(2024, 1, 1);
        SupplyContract contract = new SupplyContract.Builder()
                .withValidDateFrom(validDateFrom)
                .build();
        Supply supply = SupplyMother.random(user)
                .withShelly(null)
                .withDistributor(null)
                .withContract(contract)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNull(result.getShelly());
        assertNull(result.getDistributor());
        assertNotNull(result.getContract());
        assertEquals(validDateFrom, result.getContract().getValidDateFrom());
    }

    @Test
    void shouldCreateSupplyWithOnlyMandatoryFields() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = new Supply.Builder()
                .withCode(RandomStringUtils.random(20, true, true))
                .withAddress(RandomStringUtils.random(20, true, true))
                .withUser(user)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertEquals(supply.getName(), result.getName());
        assertEquals(supply.getAddress(), result.getAddress());
        assertEquals(user.getId(), result.getUser().getId());
        assertEquals(supply.getEnabled(), false);
        assertNull(result.getAddressRef());
        assertNull(result.getShelly());
        assertNull(result.getDistributor());
        assertNull(result.getContract());
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserDoesNotExist() {
        // Arrange
        UserId nonExistentUserId = UserId.of(UUID.randomUUID());
        Supply supply = SupplyMother.random().build();

        // Act & Assert
        assertThrows(UserNotFoundException.class,
                () -> createSupplyRepository.create(supply, nonExistentUserId, DEFAULT_COMMUNITY_ID));
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenCommunityDoesNotExist() {
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();
        UUID nonExistentCommunityId = UUID.randomUUID();

        assertThrows(CommunityNotFoundException.class,
                () -> createSupplyRepository.create(supply, UserId.of(user.getId()), nonExistentCommunityId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM supplies WHERE code = ?", Integer.class,
                supply.getCode()));
    }

    @Test
    void testSupplyLinkedToCommunityIsReadBack() {
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random(user).build();
        Supply created = createSupplyRepository.create(supply, UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        assertNotNull(created.getCommunity(), "Supply created via service should be linked to the default community");
        assertEquals(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"), created.getCommunity().getId());
    }
}
