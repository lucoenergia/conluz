package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nothing runs in a test transaction: each row is created in a transaction of its own, which a test transaction would
 * swallow, so that the first failing row would roll the others back. Every community and user a test creates is
 * deleted afterwards. Every CUPS is random, because the CUPS is unique across communities and other tests commit theirs.
 */
class ImportSuppliesControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/{communityId}/supplies/import";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> communities = new CopyOnWriteArrayList<>();
    private final List<UUID> users = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteWhatWasCreated() {
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM supplies WHERE community_id = ?", community);
            jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community);
        }
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM supplies WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        communities.forEach(id -> jdbcTemplate.update("DELETE FROM communities WHERE id = ?", id));
    }

    // --- The file as spreadsheets export it ---

    @Test
    @DisplayName("SUP-005 a file as Excel saves CSV on Windows, in Windows-1252 and separated by semicolons, creates "
            + "each valid row with its accents intact, and fails the others, each result on its own line")
    void aWindows1252File_createsEachValidRowWithItsAccents() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String first = SupplyMother.randomCups();
        String second = SupplyMother.randomCups();
        String content = "code;address;personalId;addressRef;name\r\n"
                + first + ";Plaza de la Constitución 1, Luco;" + owner.getPersonalId() + ";Bajo A;Peña\r\n"
                + "ES12;Calle Mayor 2;" + owner.getPersonalId() + ";;\r\n"
                + second + ";\"Camino del Pajar, s/n, Pingüino\";" + owner.getPersonalId() + ";;Muñoz\r\n"
                + SupplyMother.randomCups() + ";;" + owner.getPersonalId() + ";;\r\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(),
                file(content.getBytes(WINDOWS_1252), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(2)));

        assertThat(lines(response)).containsExactly(2, 3, 4, 5);
        assertThat(outcomes(response)).containsExactly("CREATED", "FAILED", "CREATED", "FAILED");
        assertThat(codes(response)).containsExactly(null, "SUPPLY_IMPORT_ROW_INVALID", null,
                "SUPPLY_IMPORT_ROW_INVALID");
        Map<String, Supply> stored = suppliesByCode(community);
        assertThat(stored.keySet()).containsExactlyInAnyOrder(first, second);
        assertThat(stored.get(first).getAddress()).isEqualTo("Plaza de la Constitución 1, Luco");
        assertThat(stored.get(first).getName()).isEqualTo("Peña");
        assertThat(stored.get(second).getAddress()).isEqualTo("Camino del Pajar, s/n, Pingüino");
        assertThat(stored.get(second).getName()).isEqualTo("Muñoz");
        assertThat(stored.values()).allSatisfy(supply -> {
            assertThat(supply.getUser().getId()).isEqualTo(owner.getId());
            assertThat(supply.getEnabled()).isTrue();
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"application/vnd.ms-excel", "application/octet-stream"})
    @DisplayName("SUP-005 a file is processed whatever content type it is uploaded with")
    void anyContentType_isProcessed(String contentType) throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String code = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(withBom("code,address,personalId\n" + code
                + ",Calle Mayor 1," + owner.getPersonalId() + "\n"), contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertThat(suppliesByCode(community)).containsOnlyKeys(code);
    }

    @Test
    @DisplayName("SUP-005 a file with only the required columns is accepted, and the optional ones are stored as null")
    void onlyTheRequiredColumns_areAccepted() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String code = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(utf8(" personalId ;code;address\n"
                + owner.getPersonalId() + ";" + code + ";Calle Mayor 1\n"), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        Supply stored = suppliesByCode(community).get(code);
        assertThat(stored.getName()).isNull();
        assertThat(stored.getAddressRef()).isNull();
    }

    @Test
    @DisplayName("SUP-005 empty optional cells are stored as null, and filled ones as they are; the name is not "
            + "derived from the address")
    void optionalCells_emptyAreNull_filledAreStored() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String empty = SupplyMother.randomCups();
        String filled = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(utf8("code;address;personalId;addressRef;name\n"
                + empty + ";Calle Mayor 1;" + owner.getPersonalId() + ";;\n"
                + filled + ";Calle Mayor 2;" + owner.getPersonalId() + ";Bajo B;Casa\n"), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));

        Map<String, Supply> stored = suppliesByCode(community);
        assertThat(stored.get(empty).getName()).isNull();
        assertThat(stored.get(empty).getAddressRef()).isNull();
        assertThat(stored.get(filled).getName()).isEqualTo("Casa");
        assertThat(stored.get(filled).getAddressRef()).isEqualTo("Bajo B");
    }

    // --- Whole-file refusals ---

    static Stream<Arguments> refusedFiles() {
        StringBuilder tooMany = new StringBuilder("code;address;personalId\n");
        for (int i = 0; i < 1001; i++) {
            tooMany.append(SupplyMother.randomCups()).append(";Calle ").append(i).append(";12345678Z\n");
        }
        return Stream.of(
                Arguments.of(new byte[]{(byte) 0xFF, (byte) 0xFE, 'c', 0}, "SUPPLY_IMPORT_INVALID_ENCODING", Map.of()),
                Arguments.of(utf8("code;address,personalId\n"), "SUPPLY_IMPORT_AMBIGUOUS_SEPARATOR", Map.of()),
                Arguments.of(utf8("code;address;personalId\n\"ES;Calle;1\n"), "SUPPLY_IMPORT_MALFORMED_FILE",
                        Map.of()),
                Arguments.of(utf8("code;address;personalId;Name;partitionCoefficient\n"),
                        "SUPPLY_IMPORT_UNKNOWN_HEADERS", Map.of("headers", "Name,partitionCoefficient")),
                Arguments.of(utf8("code;address;personalId;code\n"), "SUPPLY_IMPORT_DUPLICATED_HEADERS",
                        Map.of("headers", "code")),
                Arguments.of(utf8("code;address\n"), "SUPPLY_IMPORT_MISSING_HEADERS",
                        Map.of("headers", "personalId")),
                Arguments.of(utf8(tooMany.toString()), "SUPPLY_IMPORT_TOO_MANY_ROWS", Map.of("maxRows", "1000")));
    }

    @ParameterizedTest
    @MethodSource("refusedFiles")
    @DisplayName("SUP-005 a file that cannot be read, or whose header or size breaks the rules, is refused with 400 "
            + "and its code, and nothing is created")
    void aRefusedFile_createsNothing(byte[] content, String code, Map<String, String> params) throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        // A valid first row, so that a file refused as a whole is seen not to create it
        byte[] withValidRow = code.equals("SUPPLY_IMPORT_INVALID_ENCODING") || code.equals("SUPPLY_IMPORT_TOO_MANY_ROWS")
                ? content : concat(content, utf8(SupplyMother.randomCups() + ";Calle Mayor 1;" + owner.getPersonalId()
                + "\n"));

        ResultActions result = importFile(loginAsAdminOf(community), community.getId(), file(withValidRow, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(code));
        for (Map.Entry<String, String> param : params.entrySet()) {
            result.andExpect(jsonPath("$.errors[0].params." + param.getKey()).value(param.getValue()));
        }

        assertThat(suppliesByCode(community)).isEmpty();
    }

    // --- Rows ---

    @Test
    @DisplayName("SUP-005 a row with an empty required cell, or a code that is not a CUPS, fails with "
            + "SUPPLY_IMPORT_ROW_INVALID and one error per rule broken, by column, and the other rows are created")
    void invalidRows_listEveryRuleBroken() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String id = owner.getPersonalId();
        String valid = SupplyMother.randomCups();
        String content = "code;address;personalId\n"
                + ";Calle 1;" + id + "\n"
                + SupplyMother.randomCups() + "; ;" + id + "\n"
                + SupplyMother.randomCups() + ";Calle 3;\n"
                + "ES0031300325733001FH0FX;;" + id + "\n"
                + "ES00313003257330 1FH0F;Calle 5;" + id + "\n"
                + valid + ";Calle 6;" + id + "\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(), file(utf8(content),
                "text/csv")).andExpect(status().isOk()));

        assertThat(lines(response)).containsExactly(2, 3, 4, 5, 6, 7);
        assertThat(codes(response)).containsExactly("SUPPLY_IMPORT_ROW_INVALID", "SUPPLY_IMPORT_ROW_INVALID",
                "SUPPLY_IMPORT_ROW_INVALID", "SUPPLY_IMPORT_ROW_INVALID", "SUPPLY_IMPORT_ROW_INVALID", null);
        assertThat(errors(response.at("/rows/0"))).containsExactly("code:REQUIRED");
        assertThat(errors(response.at("/rows/1"))).containsExactly("address:REQUIRED");
        assertThat(errors(response.at("/rows/2"))).containsExactly("personalId:REQUIRED");
        assertThat(errors(response.at("/rows/3"))).containsExactly("code:INVALID_CUPS", "address:REQUIRED");
        assertThat(errors(response.at("/rows/4"))).containsExactly("code:INVALID_CUPS");
        assertThat(suppliesByCode(community)).containsOnlyKeys(valid);
    }

    @Test
    @DisplayName("SUP-005 a row with fewer or more cells than the header fails with SUPPLY_IMPORT_ROW_MALFORMED, and "
            + "the other rows are created")
    void malformedRows_fail() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String valid = SupplyMother.randomCups();
        String content = "code;address;personalId\n"
                + SupplyMother.randomCups() + ";Calle 1\n"
                + valid + ";Calle 2;" + owner.getPersonalId() + "\n"
                + SupplyMother.randomCups() + ";Calle 3;" + owner.getPersonalId() + ";extra\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(), file(utf8(content),
                "text/csv")).andExpect(status().isOk()));

        assertThat(lines(response)).containsExactly(2, 3, 4);
        assertThat(codes(response)).containsExactly("SUPPLY_IMPORT_ROW_MALFORMED", null,
                "SUPPLY_IMPORT_ROW_MALFORMED");
        assertThat(suppliesByCode(community)).containsOnlyKeys(valid);
    }

    @Test
    @DisplayName("SUP-002 a row whose personal ID matches no user and one whose user is not a member of the community "
            + "fail with the same code and message, and nothing is created for them")
    void unknownAndNonMemberOwners_failTheSame() throws Exception {
        Community community = newCommunity();
        Community other = newCommunity();
        User nonMember = memberOf(other, CommunityRole.COMMUNITY_MEMBER);
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String valid = SupplyMother.randomCups();
        String content = "code;address;personalId\n"
                + SupplyMother.randomCups() + ";Calle 1;" + UserMother.randomPersonalId() + "\n"
                + SupplyMother.randomCups() + ";Calle 2;" + nonMember.getPersonalId() + "\n"
                + valid + ";Calle 3;" + member.getPersonalId() + "\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(), file(utf8(content),
                "text/csv")).andExpect(status().isOk()));

        assertThat(codes(response)).containsExactly("SUPPLY_OWNER_NOT_FOUND", "SUPPLY_OWNER_NOT_FOUND", null);
        assertThat(response.at("/rows/0/message").asText()).isEqualTo(response.at("/rows/1/message").asText());
        assertThat(response.at("/rows/0/errors")).isEqualTo(response.at("/rows/1/errors"));
        assertThat(suppliesByCode(community)).containsOnlyKeys(valid);
        assertThat(suppliesByCode(other)).isEmpty();
    }

    @Test
    @DisplayName("SUP-002 a supply is created for a member whose membership is still pending")
    void aPendingMember_ownsTheSupply() throws Exception {
        Community community = newCommunity();
        User pending = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(membershipJpaRepository, pending.getId(), community.getId());
        String code = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(utf8("code;address;personalId\n" + code
                + ";Calle 1;" + pending.getPersonalId() + "\n"), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].outcome").value("CREATED"));

        assertThat(suppliesByCode(community).get(code).getUser().getId()).isEqualTo(pending.getId());
    }

    @Test
    @DisplayName("SUP-005 a personal ID typed with spaces, dots, a hyphen and a lower-case letter resolves to the "
            + "member")
    void aPersonalIdTypingVariant_resolvesToTheMember() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String id = owner.getPersonalId();
        String variant = " " + id.substring(0, 2) + "." + id.substring(2, 5) + "." + id.substring(5, 8) + "-"
                + id.substring(8).toLowerCase() + " ";
        String code = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(utf8("code;address;personalId\n" + code
                + ";Calle 1;" + variant + "\n"), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].outcome").value("CREATED"));

        assertThat(suppliesByCode(community).get(code).getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("SUP-006 a lower-case code is stored upper-cased")
    void aLowerCaseCode_isStoredUpperCased() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String code = SupplyMother.randomCups();

        importFile(loginAsAdminOf(community), community.getId(), file(utf8("code;address;personalId\n"
                + code.toLowerCase() + ";Calle 1;" + owner.getPersonalId() + "\n"), "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertThat(suppliesByCode(community)).containsOnlyKeys(code);
    }

    // --- Duplicates ---

    @Test
    @DisplayName("SUP-007 a CUPS repeated in the file, in the other length or case, creates the first row and fails "
            + "the later ones with SUPPLY_ALREADY_EXISTS")
    void aCupsRepeatedInTheFile_createsTheFirstRowOnly() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String full = SupplyMother.randomCups();
        String shortForm = full.substring(0, 20);
        String content = "code;address;personalId\n"
                + shortForm + ";Calle 1;" + owner.getPersonalId() + "\n"
                + full + ";Calle 2;" + owner.getPersonalId() + "\n"
                + full.toLowerCase() + ";Calle 3;" + owner.getPersonalId() + "\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(), file(utf8(content),
                "text/csv")).andExpect(status().isOk()));

        assertThat(codes(response)).containsExactly(null, "SUPPLY_ALREADY_EXISTS", "SUPPLY_ALREADY_EXISTS");
        assertThat(suppliesByCode(community)).containsOnlyKeys(shortForm);
    }

    @Test
    @DisplayName("SUP-007 a CUPS a supply of the community already has, in either length and in any case, fails with "
            + "SUPPLY_ALREADY_EXISTS")
    void aCupsTheCommunityHas_fails() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String storedShort = SupplyMother.randomCups().substring(0, 20);
        String storedFull = SupplyMother.randomCups();
        String storedLower = SupplyMother.randomCups().toLowerCase();
        existingSupply(owner, community, storedShort);
        existingSupply(owner, community, storedFull);
        existingSupply(owner, community, storedLower);
        String content = "code;address;personalId\n"
                + storedShort + "0F;Calle 1;" + owner.getPersonalId() + "\n"
                + storedFull.substring(0, 20) + ";Calle 2;" + owner.getPersonalId() + "\n"
                + storedLower.toUpperCase() + ";Calle 3;" + owner.getPersonalId() + "\n";

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(), file(utf8(content),
                "text/csv")).andExpect(status().isOk()));

        assertThat(codes(response)).containsExactly("SUPPLY_ALREADY_EXISTS", "SUPPLY_ALREADY_EXISTS",
                "SUPPLY_ALREADY_EXISTS");
        assertThat(suppliesByCode(community)).hasSize(3);
    }

    @Test
    @DisplayName("SUP-007 a CUPS a supply of another community has fails with SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY, "
            + "naming neither that community nor its owner")
    void aCupsAnotherCommunityHas_fails() throws Exception {
        Community community = newCommunity();
        Community other = newCommunity();
        User otherOwner = memberOf(other, CommunityRole.COMMUNITY_MEMBER);
        String code = SupplyMother.randomCups();
        existingSupply(otherOwner, other, code);
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        String response = importFile(loginAsAdminOf(community), community.getId(), file(utf8(
                "code;address;personalId\n" + code.substring(0, 20) + ";Calle 1;" + owner.getPersonalId() + "\n"),
                "text/csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].code").value("SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY"))
                .andReturn().getResponse().getContentAsString();

        for (String named : List.of(other.getName(), other.getId().toString(), otherOwner.getFullName(),
                otherOwner.getPersonalId())) {
            assertThat(response).doesNotContain(named);
        }
        assertThat(suppliesByCode(community)).isEmpty();
    }

    @Test
    @DisplayName("SUP-005 importing the same file again fails every row already created with SUPPLY_ALREADY_EXISTS "
            + "and creates the rest")
    void reImportingTheSameFile_createsOnlyTheRest() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User later = newUser();
        String first = SupplyMother.randomCups();
        String second = SupplyMother.randomCups();
        String pending = SupplyMother.randomCups();
        MockMultipartFile file = file(utf8("code;address;personalId\n"
                + first + ";Calle 1;" + owner.getPersonalId() + "\n"
                + pending + ";Calle 2;" + later.getPersonalId() + "\n"
                + second + ";Calle 3;" + owner.getPersonalId() + "\n"), "text/csv");
        String bearer = loginAsAdminOf(community);

        assertThat(codes(json(importFile(bearer, community.getId(), file))))
                .containsExactly(null, "SUPPLY_OWNER_NOT_FOUND", null);
        createMembershipRepository.create(community.getId(), later.getId(), CommunityRole.COMMUNITY_MEMBER);

        JsonNode again = json(importFile(bearer, community.getId(), file)
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(2)));

        assertThat(codes(again)).containsExactly("SUPPLY_ALREADY_EXISTS", null, "SUPPLY_ALREADY_EXISTS");
        assertThat(suppliesByCode(community)).containsOnlyKeys(first, second, pending);
    }

    // --- The result ---

    @Test
    @DisplayName("SUP-005 a row result carries exactly its line, outcome, code, message and errors, and no value of "
            + "the file")
    void aRowResult_carriesNoValueOfTheFile() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String created = SupplyMother.randomCups();
        String duplicate = created.substring(0, 20);
        String unknown = UserMother.randomPersonalId();
        String unknownCode = SupplyMother.randomCups();
        String content = "code;address;personalId\n"
                + created + ";Calle Secreta 1;" + owner.getPersonalId() + "\n"
                + duplicate + ";Calle Secreta 2;" + owner.getPersonalId() + "\n"
                + unknownCode + ";Calle Secreta 3;" + unknown + "\n"
                + "ES1;Calle Secreta 4;" + owner.getPersonalId() + "\n";

        String body = importFile(loginAsAdminOf(community), community.getId(), file(utf8(content), "text/csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = json(body);
        assertThat(fieldNames(response)).containsExactlyInAnyOrder("created", "failed", "rows");
        for (JsonNode row : response.get("rows")) {
            assertThat(fieldNames(row)).containsExactlyInAnyOrder("line", "outcome", "code", "message", "errors");
        }
        for (String value : List.of(created, duplicate, unknownCode, "ES1", owner.getPersonalId(), unknown,
                "Calle Secreta")) {
            assertThat(body).doesNotContain(value);
        }
    }

    @Test
    @DisplayName("SUP-005 every row message is in the language of the request's Accept-Language")
    void rowMessages_followAcceptLanguage() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String content = "code;address;personalId\n"
                + ";;" + owner.getPersonalId() + "\n"
                + SupplyMother.randomCups() + ";Calle 1\n"
                + SupplyMother.randomCups() + ";Calle 2;" + UserMother.randomPersonalId() + "\n";
        String bearer = loginAsAdminOf(community);

        JsonNode spanish = json(mockMvc.perform(multipart(URL, community.getId())
                .file(file(utf8(content), "text/csv"))
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es")));
        JsonNode english = json(mockMvc.perform(multipart(URL, community.getId())
                .file(file(utf8(content), "text/csv"))
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")));

        assertThat(messages(spanish)).containsExactly(
                "La fila tiene valores no válidos: code es obligatorio; address es obligatorio.",
                "La fila no tiene tantos valores como columnas tiene la cabecera.",
                "Ningún miembro de esta comunidad tiene este DNI/NIE/NIF. Registra primero al miembro y después crea "
                        + "el suministro.");
        assertThat(messages(english)).containsExactly(
                "The row has invalid values: code is required; address is required.",
                "The row does not have as many values as the header has columns.",
                "No member of this community has this DNI/NIE/NIF. Register the member first, then create the "
                        + "supply.");
    }

    // --- Who may import ---

    @Test
    @DisplayName("SUP-005 a platform admin who is not a community admin here is refused as createSupply refuses it, "
            + "and nothing is created")
    void aPlatformAdmin_isRefused() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        User admin = newUser();
        managePlatformAdminRepository.grant(UserId.of(admin.getId()));

        assertRefusedLikeCreateSupply(loginUser(admin), community, owner, 403);
    }

    @Test
    @DisplayName("SUP-005 a plain member of the community is refused as createSupply refuses it, and nothing is "
            + "created")
    void aPlainMember_isRefused() throws Exception {
        Community community = newCommunity();
        User owner = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        assertRefusedLikeCreateSupply(loginUser(memberOf(community, CommunityRole.COMMUNITY_MEMBER)), community,
                owner, 403);
    }

    private void assertRefusedLikeCreateSupply(String bearer, Community community, User owner, int status)
            throws Exception {
        String code = SupplyMother.randomCups();
        importFile(bearer, community.getId(), file(utf8("code;address;personalId\n" + code + ";Calle 1;"
                + owner.getPersonalId() + "\n"), "text/csv"))
                .andExpect(status().is(status));
        mockMvc.perform(post("/api/v1/supplies")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "address", "Calle 1",
                                "personalId", owner.getPersonalId(), "communityId", community.getId()))))
                .andExpect(status().is(status));

        assertThat(suppliesByCode(community)).isEmpty();
    }

    // --- Fixtures, created the way production creates them ---

    private Community newCommunity() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        return community;
    }

    private User newUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        users.add(user.getId());
        return user;
    }

    private User memberOf(Community community, CommunityRole role) {
        User user = newUser();
        createMembershipRepository.create(community.getId(), user.getId(), role);
        return user;
    }

    private String loginAsAdminOf(Community community) throws Exception {
        return loginUser(memberOf(community, CommunityRole.COMMUNITY_ADMIN));
    }

    /**
     * Through the repository, which does not check the code, so that a code stored before the CUPS rules, such as a
     * lower-case one, can be set up too.
     */
    private void existingSupply(User owner, Community community, String code) {
        createSupplyRepository.create(SupplyMother.random(owner).withCode(code).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private Map<String, Supply> suppliesByCode(Community community) {
        Map<String, Supply> byCode = new java.util.HashMap<>();
        for (Supply supply : getSupplyRepository.findAllByCommunityId(community.getId())) {
            byCode.put(supply.getCode(), supply);
        }
        return byCode;
    }

    // --- Requests and responses ---

    private ResultActions importFile(String bearer, UUID communityId, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart(URL, communityId)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static MockMultipartFile file(byte[] content, String contentType) {
        return new MockMultipartFile("file", "supplies.csv", contentType, content);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withBom(String text) {
        return concat(BOM, utf8(text));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] content = new byte[first.length + second.length];
        System.arraycopy(first, 0, content, 0, first.length);
        System.arraycopy(second, 0, content, first.length, second.length);
        return content;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return json(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private static List<Integer> lines(JsonNode response) {
        List<Integer> lines = new ArrayList<>();
        response.get("rows").forEach(row -> lines.add(row.get("line").asInt()));
        return lines;
    }

    private static List<String> outcomes(JsonNode response) {
        List<String> outcomes = new ArrayList<>();
        response.get("rows").forEach(row -> outcomes.add(row.get("outcome").asText()));
        return outcomes;
    }

    private static List<String> codes(JsonNode response) {
        List<String> codes = new ArrayList<>();
        response.get("rows").forEach(row -> codes.add(row.get("code").isNull() ? null : row.get("code").asText()));
        return codes;
    }

    private static List<String> messages(JsonNode response) {
        List<String> messages = new ArrayList<>();
        response.get("rows").forEach(row -> messages.add(row.get("message").asText()));
        return messages;
    }

    private static List<String> errors(JsonNode row) {
        List<String> errors = new ArrayList<>();
        row.get("errors").forEach(error -> errors.add(error.get("column").asText() + ":"
                + error.get("constraint").asText()));
        return errors;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
