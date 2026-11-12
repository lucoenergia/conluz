package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The old production reads, superseded by the community, plant and supply energy operations, are
 * deprecated in the generated contract and each names its replacement (#570).
 */
class DeprecatedProductionReadsApiDocsTest extends BaseControllerTest {

    private static final String INSTANT =
            "Deprecated: use `getPlantInverterProductionToday`.";
    private static final String HUAWEI_SERIES =
            "Deprecated: use `getCommunityEnergyMetrics` and `getCommunity{Hourly,Daily,Monthly}EnergySeries` for "
                    + "distributor data; `getPlantInverterProductionToday` for today's inverter data.";
    private static final String DATADIS_SERIES =
            "Deprecated: use `getCommunityEnergyMetrics` and `getCommunity*EnergySeries` for the community; "
                    + "`getPlantEnergyMetrics` and `getPlant*EnergySeries` for a plant.";
    private static final String SUPPLY_SERIES =
            "Deprecated: use `getSupplyEnergyMetrics` and the supply consumption series (`assignedProductionKWh`).";

    private static final Map<String, String> REPLACEMENT_LINES = Map.ofEntries(
            Map.entry("getInstantProduction", INSTANT),
            Map.entry("getHourlyProduction", HUAWEI_SERIES),
            Map.entry("getDailyProduction", HUAWEI_SERIES),
            Map.entry("getMonthlyProduction", HUAWEI_SERIES),
            Map.entry("getYearlyProduction", HUAWEI_SERIES),
            Map.entry("getDatadisHourlyProduction", DATADIS_SERIES),
            Map.entry("getDatadisDailyProduction", DATADIS_SERIES),
            Map.entry("getDatadisMonthlyProduction", DATADIS_SERIES),
            Map.entry("getDatadisYearlyProduction", DATADIS_SERIES),
            Map.entry("getSupplyHourlyProduction", SUPPLY_SERIES),
            Map.entry("getSupplyDailyProduction", SUPPLY_SERIES),
            Map.entry("getSupplyMonthlyProduction", SUPPLY_SERIES));

    /** Still in use, so never deprecated with the reads (#570). */
    private static final List<String> SYNC_AND_HUAWEI_CONFIGURATION = List.of(
            "syncMonthlyDatadisProduction", "syncYearlyDatadisProduction",
            "syncMonthlyHuaweiProduction", "syncYearlyHuaweiProduction",
            "getHuaweiConfig", "configureHuawei", "enableHuaweiConfig", "disableHuaweiConfig");

    /** A backticked name in a replacement line, unless it is a pattern or a field. */
    private static final Pattern NAMED_OPERATION = Pattern.compile("`([A-Za-z]+)`");
    private static final Set<String> NAMED_FIELDS = Set.of("assignedProductionKWh");

    @Test
    @DisplayName("API-002 each old production read is deprecated and its description opens with its replacement")
    void eachOldProductionReadIsDeprecatedAndNamesItsReplacement() throws Exception {
        Map<String, JsonNode> operations = operationsById();

        REPLACEMENT_LINES.forEach((operationId, line) -> {
            JsonNode operation = operations.get(operationId);
            assertTrue(operation != null, () -> "No operation " + operationId);
            assertTrue(operation.path("deprecated").asBoolean(false), () -> operationId + " is not deprecated");
            String description = operation.path("description").asText();
            assertTrue(description.startsWith(line + "\n\n"),
                    () -> operationId + " does not open with its replacement line: " + description);
        });
    }

    @Test
    @DisplayName("API-002 every other operation is not deprecated, the production syncs and Huawei configuration included")
    void everyOtherOperationIsNotDeprecated() throws Exception {
        Map<String, JsonNode> operations = operationsById();

        for (String operationId : SYNC_AND_HUAWEI_CONFIGURATION) {
            assertTrue(operations.containsKey(operationId), () -> "No operation " + operationId);
        }
        List<String> deprecated = new ArrayList<>();
        operations.forEach((operationId, operation) -> {
            if (operation.path("deprecated").asBoolean(false)) {
                deprecated.add(operationId);
            }
        });
        assertEquals(REPLACEMENT_LINES.keySet(), new HashSet<>(deprecated));
    }

    @Test
    @DisplayName("API-002 every operation a replacement line names exists in the contract")
    void everyNamedReplacementExists() throws Exception {
        Map<String, JsonNode> operations = operationsById();

        for (String line : new HashSet<>(REPLACEMENT_LINES.values())) {
            Matcher matcher = NAMED_OPERATION.matcher(line);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!NAMED_FIELDS.contains(name)) {
                    assertTrue(operations.containsKey(name), () -> "No operation " + name + " named in: " + line);
                    assertFalse(operations.get(name).path("deprecated").asBoolean(false),
                            () -> "The replacement " + name + " is itself deprecated");
                }
            }
        }
    }

    private Map<String, JsonNode> operationsById() throws Exception {
        Map<String, JsonNode> operations = new HashMap<>();
        document().path("paths").forEach(path -> path.forEach(operation -> {
            if (operation.has("operationId")) {
                operations.put(operation.path("operationId").asText(), operation);
            }
        }));
        return operations;
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
