package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSupplyControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";

    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;

    @Test
    void testCreateSupplyWithoutName() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);

        String body = String.format("""
                {
                  "code": "ES0033333333333333AA0A",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD",
                  "partitionCoefficient": "3.0763"
                }
        """, userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ES0033333333333333AA0A"))
                .andExpect(jsonPath("$.address").value("Fake Street 123"))
                .andExpect(jsonPath("$.addressRef").value("4ASDF654ASDF89ASD"))
                .andExpect(jsonPath("$.partitionCoefficient").value("3.0763"))
                .andExpect(jsonPath("$.name").value("Fake Street 123")) // Name is set to address by default
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").value(user.getPersonalId()))
                .andExpect(jsonPath("$.user.number").value(user.getNumber()))
                .andExpect(jsonPath("$.user.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.user.address").value(user.getAddress()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.phoneNumber").value(user.getPhoneNumber()))
                .andExpect(jsonPath("$.user.enabled").value(user.isEnabled()));

        Assertions.assertEquals(1, supplyRepository.countByCode("ES0033333333333333AA0A"));
    }

    @Test
    void testCreateSupplyWithName() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);

        String body = String.format("""
                {
                  "code": "ES0033333333333333BB0B",
                  "personalId": "%s",
                  "address": "Fake Street 456",
                  "addressRef": "4ASDF654ASDF89ASD",
                  "partitionCoefficient": "2.5432",
                  "name": "Test Supply Name"
                }
        """, userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ES0033333333333333BB0B"))
                .andExpect(jsonPath("$.address").value("Fake Street 456"))
                .andExpect(jsonPath("$.addressRef").value("4ASDF654ASDF89ASD"))
                .andExpect(jsonPath("$.partitionCoefficient").value("2.5432"))
                .andExpect(jsonPath("$.name").value("Test Supply Name")) // Name is explicitly provided
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").value(user.getPersonalId()))
                .andExpect(jsonPath("$.user.number").value(user.getNumber()))
                .andExpect(jsonPath("$.user.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.user.address").value(user.getAddress()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.phoneNumber").value(user.getPhoneNumber()))
                .andExpect(jsonPath("$.user.enabled").value(user.isEnabled()));

        Assertions.assertEquals(1, supplyRepository.countByCode("ES0033333333333333BB0B"));
    }

    @Test
    void testWithDuplicatedSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        createSupplyService.create(supply, UserId.of(user.getId()));

        String body = String.format("""
                {
                  "code": "%s",
                  "personalId": "%s",
                  "address": "%s",
                  "addressRef": "%s",
                  "partitionCoefficient": "%s"
                }
        """, supply.getCode(), user.getPersonalId(), supply.getAddress(), supply.getAddressRef(),
                supply.getPartitionCoefficient());

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
                          "personalId": "54889216G",
                          "address": "Fake Street 456",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "partitionCoefficient": "2.5432"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "address": "Fake Street 456",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "partitionCoefficient": "2.5432"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "personalId": "54889216G",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "partitionCoefficient": "2.5432"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "personalId": "54889216G",
                          "addressRef": "4ASDF654ASDF89ASD",
                          "address": "Fake Street 456"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "personalId": "54889216G",
                          "partitionCoefficient": "2.5432",
                          "address": "Fake Street 456"
                        }
                """
                );
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
                      "code": "ES0033333333333333AA0A",
                      "personalId": "54889216G",
                      "address": "Fake Street 123",
                      "addressRef": "4ASDF654ASDF89ASD",
                      "partitionCoefficient": "-3.0763"
                    }
                """,
                """
                    {
                      "code": "ES0033333333333333AA0A",
                      "personalId": "54889216G",
                      "address": "Fake Street 123",
                      "addressRef": "4ASDF654ASDF89ASD",
                      "partitionCoefficient": "3,0763"
                    }
                """,
                """
                    {
                      "code": "ES0033333333333333AA0A",
                      "personalId": "54889216G",
                      "address": "Fake Street 123",
                      "addressRef": "4ASDF654ASDF89ASD",
                      "partitionCoefficient": "foo"
                    }
                """);
    }

    @Test
    void testWithoutBody() throws Exception {
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

        String userPersonalId = "54889216G";
        String body = String.format("""
                {
                  "code": "ES0033333333333333AA0A",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD",
                  "partitionCoefficient": "3.0763"
                }
        """, userPersonalId);

        mockMvc.perform(post(URL)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
