package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetailMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capability field names are the contract conluz-web is written against, and they are produced
 * by Jackson from getters named {@code isCanRead()} rather than declared anywhere. A getter renamed
 * to {@code getCanRead()}, or a field renamed without its getter, would change the JSON key and
 * break every client silently -- the schema in the OpenAPI document would change with it, so both
 * would agree on the wrong thing.
 *
 * <p>These assertions are on the serialised keys, so they fail on exactly that.</p>
 */
class CapabilitySerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyCapabilityIsSerialisedUnderItsCanSomethingName() throws Exception {
        assertKeys(PlatformCapabilitiesResponse.builder().build(),
                Set.of("canCreateCommunity", "canListUsers", "canAdministerPlatform", "canCreateUsers"));

        assertKeys(CommunityCapabilitiesResponse.builder().build(),
                Set.of("canRead", "canUpdate", "canEnable", "canDisable", "canManage",
                        "canManageMemberships", "canManageMembershipInvestment", "canListPlants",
                        "canCreatePlants", "canCreateUsers", "canReadProduction", "canListSupplies"));

        assertKeys(SupplyCapabilitiesResponse.builder().build(),
                Set.of("canRead", "canEdit", "canReadPartitionCoefficients", "canCreatePlant"));

        assertKeys(PlantCapabilitiesResponse.builder().build(),
                Set.of("canRead", "canManage", "canListSharingAgreements", "canManageSharingAgreements",
                        "canReadSupply"));

        assertKeys(SharingAgreementCapabilitiesResponse.builder().build(),
                Set.of("canRead", "canManage"));

        assertKeys(UserCapabilitiesResponse.builder().build(),
                Set.of("canRead", "canEdit", "canDelete", "canEnable", "canDisable",
                        "canGrantPlatformAdmin", "canRevokePlatformAdmin", "canListSupplies"));

        assertKeys(MembershipCapabilitiesResponse.builder().build(),
                Set.of("canUpdateRole", "canDelete", "canManageInvestment", "canReadPayback"));

        assertKeys(PartitionCoefficientCapabilitiesResponse.builder().build(),
                Set.of("canReadSharingAgreement"));
    }

    /**
     * Every capability is a primitive boolean, so the key is always present and the value is never
     * null -- which is what lets the schema declare all of them required and non-nullable.
     */
    @Test
    void everyCapabilityIsAPresentNonNullBoolean() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                SupplyCapabilitiesResponse.builder().withCanRead(true).build()));

        assertTrue(json.get("canRead").isBoolean());
        assertTrue(json.get("canRead").asBoolean());
        assertTrue(json.get("canEdit").isBoolean(), "an unset capability is false, not absent or null");
        assertEquals(false, json.get("canEdit").asBoolean());
    }

    /**
     * The coefficient period carries its capabilities under the same key every other resource uses,
     * and the unset value is a present false -- conluz-web's visual fixtures are untyped, so an absent
     * key would read as false there without anyone noticing.
     */
    @Test
    void aCoefficientPeriodCarriesItsCapabilitiesUnderCapabilities() throws Exception {
        PartitionCoefficientResponse response = new PartitionCoefficientResponse(
                SupplyPartitionCoefficientDetailMother.random(BigDecimal.ONE, Instant.parse("2025-01-01T00:00:00Z"), null),
                PartitionCoefficientCapabilitiesResponse.builder().withCanReadSharingAgreement(true).build());

        JsonNode json = objectMapper.findAndRegisterModules().readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("capabilities").get("canReadSharingAgreement").asBoolean());
        JsonNode unset = objectMapper.readTree(objectMapper.writeValueAsString(
                PartitionCoefficientCapabilitiesResponse.builder().build()));
        assertTrue(unset.get("canReadSharingAgreement").isBoolean(), "an unset capability is false, not absent");
    }

    private void assertKeys(Object capabilities, Set<String> expected) throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(capabilities));
        Set<String> actual = new HashSet<>();
        json.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, capabilities.getClass().getSimpleName() + " -> " + json);
    }
}
