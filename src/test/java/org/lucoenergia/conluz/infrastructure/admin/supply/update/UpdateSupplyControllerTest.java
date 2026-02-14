package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateSupplyControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DateConverter dateConverter;

    @Test
    void testUpdateSupplyModifyingAll() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        // Creates two users
        User userOne = UserMother.randomUser();
        userOne = createUserRepository.create(userOne);

        // Creates one supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(userOne.getId()));

        // Modify data of the supply
        UpdateSupplyBody supplyModified = new UpdateSupplyBody();
        supplyModified.setCode("code");
        supplyModified.setName("name");
        supplyModified.setAddress("address");
        supplyModified.setAddressRef("4ASDF654ASDF89ASD");
        supplyModified.setPartitionCoefficient(1.2f);

        String bodyAsString = objectMapper.writeValueAsString(supplyModified);

        mockMvc.perform(put(String.format("%s/%s", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(supplyModified.getCode()))
                .andExpect(jsonPath("$.name").value(supplyModified.getName()))
                .andExpect(jsonPath("$.address").value(supplyModified.getAddress()))
                .andExpect(jsonPath("$.addressRef").value(supplyModified.getAddressRef()))
                .andExpect(jsonPath("$.partitionCoefficient").value(supplyModified.getPartitionCoefficient()))
                .andExpect(jsonPath("$.enabled").value(supply.getEnabled()))
                .andExpect(jsonPath("$.datadisValidDateFrom").value(supply.getValidDateFrom().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                .andExpect(jsonPath("$.datadisDistributor").value(supply.getDistributor()))
                .andExpect(jsonPath("$.datadisDistributorCode").value(supply.getDistributorCode()))
                .andExpect(jsonPath("$.datadisPointType").value(supply.getPointType()))
                .andExpect(jsonPath("$.datadisIsThirdParty").value(supply.isThirdParty()))
                .andExpect(jsonPath("$.shellyMac").value(supply.getShellyMac()))
                .andExpect(jsonPath("$.shellyId").value(supply.getShellyId()))
                .andExpect(jsonPath("$.shellyMqttPrefix").value(supply.getShellyMqttPrefix()))
                .andExpect(jsonPath("$.user.personalId").value(userOne.getPersonalId()));
    }

    @Test
    void testWithUnknownSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        final String supplyId = UUID.randomUUID().toString();

        String body = """
                        {
                          "code": "code",
                          "name": "my home",
                          "address": "Fake Street 123",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "partitionCoefficient": 1.2
                        }
                """;

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithUnknownFields() throws Exception {

        final String body = """
                        {
                          "unknown": 1,
                          "code": "code",
                          "address": "Fake Street 123",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "partitionCoefficient": 1.2
                        }
                """;

        final String authHeader = loginAsDefaultAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        final String authHeader = loginAsDefaultAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        return List.of(
                """
                                {
                                  "address": "Fake Street 123",
                                  "addressRef": "4ASDF654ASDF89ASD",
                                  "partitionCoefficient": 1.2
                                }
                        """,
                """
                                {
                                  "code": "code",
                                  "addressRef": "4ASDF654ASDF89ASD",
                                  "partitionCoefficient": 1.2
                                }
                        """,
                """
                                {
                                  "code": "code",
                                  "addressRef": "4ASDF654ASDF89ASD",
                                  "address": "Fake Street 123",
                                }
                        """,
                """
                                {
                                  "code": "code",
                                  "address": "Fake Street 123",
                                  "partitionCoefficient": 1.2
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        final String authHeader = loginAsDefaultAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidFormatValues() {
        return List.of("""
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "partitionCoefficient": 0.021255,
                                 "personalId": "personalId",
                                 "enabled": true,
                                 "validDateFrom": "2023-05-01"
                             }
                        """,
                """
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "partitionCoefficient": 0.021255,
                                 "personalId": "personalId",
                                 "enabled": true,
                                 "pointType": "foo"
                             }
                        """,
                """
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "partitionCoefficient": 0.021255,
                                 "personalId": "personalId",
                                 "enabled": "foo"
                             }
                        """);
    }

    @Test
    void
    testWithoutBody() throws Exception {
        final String authHeader = loginAsDefaultAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void
    testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
