package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class CreateSupplyRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;


    @Test
    void shouldCreateSupplyWithAllOneToOneRelations() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertEquals(supply.getName(), result.getName());
        assertEquals(supply.getAddress(), result.getAddress());
        assertEquals(supply.getAddressRef(), result.getAddressRef());
        assertEquals(supply.getPartitionCoefficient(), result.getPartitionCoefficient());
        assertEquals(supply.getEnabled(), result.getEnabled());
        assertEquals(user.getId(), result.getUser().getId());

        assertNotNull(result.getShelly());
        assertEquals(supply.getShelly().getId(), result.getShelly().getId());
        assertEquals(supply.getShelly().getMacAddress(), result.getShelly().getMacAddress());
        assertEquals(supply.getShelly().getMqttPrefix(), result.getShelly().getMqttPrefix());

        assertNotNull(result.getDatadis());
        assertEquals(supply.getDatadis().isThirdParty(), result.getDatadis().isThirdParty());

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
                .withDatadis(null)
                .withDistributor(null)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertNull(result.getShelly());
        assertNull(result.getDatadis());
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
                .withDatadis(null)
                .withDistributor(null)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNotNull(result.getShelly());
        assertEquals("shelly-id-01", result.getShelly().getId());
        assertEquals("AA:BB:CC:DD:EE:FF", result.getShelly().getMacAddress());
        assertEquals("shellyplus1pm", result.getShelly().getMqttPrefix());
        assertNull(result.getDatadis());
        assertNull(result.getDistributor());
        assertNull(result.getContract());
    }

    @Test
    void shouldCreateSupplyWithOnlyDatadis() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        SupplyDatadis datadis = new SupplyDatadis.Builder()
                .withThirdParty(true)
                .build();
        Supply supply = SupplyMother.random(user)
                .withShelly(null)
                .withDatadis(datadis)
                .withDistributor(null)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNull(result.getShelly());
        assertNotNull(result.getDatadis());
        assertTrue(result.getDatadis().isThirdParty());
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
                .withDatadis(null)
                .withDistributor(distributor)
                .withContract(null)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNull(result.getShelly());
        assertNull(result.getDatadis());
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
                .withDatadis(null)
                .withDistributor(null)
                .withContract(contract)
                .build();

        // Act
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNull(result.getShelly());
        assertNull(result.getDatadis());
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
        Supply result = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(supply.getCode(), result.getCode());
        assertEquals(supply.getName(), result.getName());
        assertEquals(supply.getAddress(), result.getAddress());
        assertEquals(supply.getEnabled(), result.getEnabled());
        assertEquals(user.getId(), result.getUser().getId());
        assertNull(result.getAddressRef());
        assertNull(result.getPartitionCoefficient());
        assertNull(result.getShelly());
        assertNull(result.getDatadis());
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
                () -> createSupplyRepository.create(supply, nonExistentUserId));
    }

    @Test
    void shouldThrowSupplyAlreadyExistsExceptionWhenCodeAlreadyExists() {
        // Arrange
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = SupplyMother.random(user).build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        Supply duplicate = SupplyMother.random(user)
                .withCode(supply.getCode())
                .build();

        // Act & Assert
        assertThrows(SupplyAlreadyExistsException.class,
                () -> createSupplyRepository.create(duplicate, UserId.of(user.getId())));
    }
}
