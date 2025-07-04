package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreatePlantControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/plants";


    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;

    @Test
    void testFullBody() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String userPersonalId = "54889216G";
        UserEntity user = UserMother.randomUserEntity();
        user.setPersonalId(userPersonalId);
        userRepository.save(user);
        SupplyEntity supply = SupplyMother.randomEntity().withUser(user).build();
        supplyRepository.save(supply);

        String plantCode = "PS-456798";

        String body = String.format("""
                        {
                          "code": "%s",
                          "name": "Plant one",
                          "supplyCode": "%s",
                          "address": "Fake Street 123",
                          "description": "Plant number one",
                          "totalPower": "60.00",
                          "connectionDate": "2024-05-23",
                          "inverterProvider": "HUAWEI"
                        }
                """, plantCode, supply.getCode());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value(plantCode))
                .andExpect(jsonPath("$.address").value("Fake Street 123"))
                .andExpect(jsonPath("$.name").value("Plant one"))
                .andExpect(jsonPath("$.description").value("Plant number one"))
                .andExpect(jsonPath("$.totalPower").value("60.0"))
                .andExpect(jsonPath("$.connectionDate").value("2024-05-23"))
                .andExpect(jsonPath("$.inverterProvider").value("HUAWEI"))
                .andExpect(jsonPath("$.supply.code").value(supply.getCode()));

        Assertions.assertEquals(1, plantRepository.countByCode(plantCode));
    }

    @Test
    void testMinimumBody() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String userPersonalId = "54889216G";
        UserEntity user = UserMother.randomUserEntity();
        user.setPersonalId(userPersonalId);
        userRepository.save(user);
        SupplyEntity supply = SupplyMother.randomEntity().withUser(user).build();
        supplyRepository.save(supply);


        String plantCode = "PS-456798";

        String body = String.format("""
                        {
                          "code": "%s",
                          "name": "Plant one",
                          "supplyCode": "%s",
                          "address": "Fake Street 123",
                          "totalPower": "60.00",
                          "inverterProvider": "HUAWEI"
                        }
                """, plantCode, supply.getCode());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value(plantCode))
                .andExpect(jsonPath("$.address").value("Fake Street 123"))
                .andExpect(jsonPath("$.name").value("Plant one"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.totalPower").value("60.0"))
                .andExpect(jsonPath("$.connectionDate").isEmpty())
                .andExpect(jsonPath("$.inverterProvider").value("HUAWEI"))
                .andExpect(jsonPath("$.supply.code").value(supply.getCode()));

        Assertions.assertEquals(1, plantRepository.countByCode(plantCode));
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
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
        return List.of("""
                                {
                                  "name": "Plant one",
                                  "personalId": "12345678Z",
                                  "address": "Fake Street 123",
                                  "totalPower": "60.00",
                                  "inverterProvider": "HUAWEI"
                                }
                        """,
                """
                                {
                                  "code": "TS-65987",
                                  "personalId": "12345678Z",
                                  "address": "Fake Street 123",
                                  "totalPower": "60.00",
                                  "inverterProvider": "HUAWEI"
                                }
                        """,
                """
                                {
                                  "code": "TS-65987",
                                  "name": "Plant one",
                                  "address": "Fake Street 123",
                                  "totalPower": "60.00",
                                  "inverterProvider": "HUAWEI"
                                }
                        """,
                """
                                {
                                  "code": "TS-65987",
                                  "name": "Plant one",
                                  "personalId": "12345678Z",
                                  "totalPower": "60.00",
                                  "inverterProvider": "HUAWEI"
                                }
                        """,
                """
                                {
                                  "code": "TS-65987",
                                  "name": "Plant one",
                                  "personalId": "12345678Z",
                                  "address": "Fake Street 123",
                                  "inverterProvider": "HUAWEI"
                                }
                        """,
                """
                                {
                                  "code": "TS-65987",
                                  "name": "Plant one",
                                  "personalId": "12345678Z",
                                  "address": "Fake Street 123",
                                  "totalPower": "60.00"
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
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
                              "code": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "foo",
                              "inverterProvider": "HUAWEI"
                            }
                        """,
                """
                            {
                              "code": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "60.00",
                              "inverterProvider": "foo"
                            }
                        """,
                """
                            {
                              "code": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "60.00",
                              "connectionDate": "2024/05/23",
                              "inverterProvider": "HUAWEI"
                            }
                        """);
    }

    @Test
    void testWithDuplicatedPlant() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        UserEntity userOne = UserMother.randomUserEntity();
        userRepository.save(userOne);
        SupplyEntity supplyOne = SupplyMother.randomEntity().withUser(userOne).build();
        supplyRepository.save(supplyOne);

        String plantCode = "PS-456798";

        plantRepository.save(new PlantEntity.Builder()
                .withCode(plantCode)
                .withTotalPower(23D)
                .withAddress("Fake Street 123")
                .withInverterProvider(InverterProvider.HUAWEI)
                .withId(UUID.randomUUID())
                .withName("A name")
                .withSupply(supplyOne)
                .build());

        String body = String.format("""
                        {
                          "code": "%s",
                          "name": "Plant one",
                          "supplyCode": "%s",
                          "address": "Fake Street 123",
                          "totalPower": "60.00",
                          "inverterProvider": "HUAWEI"
                        }
                """, plantCode, supplyOne.getCode());

        mockMvc.perform(post(URL)
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

    @Test
    void
    testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
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

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        String plantCode = "PS-456798";
        String supplyCode = "123456";

        String body = String.format("""
                        {
                          "code": "%s",
                          "name": "Plant one",
                          "supplyCode": "%s",
                          "address": "Fake Street 123",
                          "description": "Plant number one",
                          "totalPower": "60.00",
                          "connectionDate": "2024-05-23",
                          "inverterProvider": "HUAWEI"
                        }
                """, plantCode, supplyCode);

        // Test users endpoint
        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
