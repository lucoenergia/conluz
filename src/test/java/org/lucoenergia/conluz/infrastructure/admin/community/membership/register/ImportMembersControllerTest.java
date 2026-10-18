package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The server can send invitations, so that nothing being sent is down to the import alone. Existing members are
 * registered through the registration endpoint, as production registers them.
 */
class ImportMembersControllerTest extends BaseSendingMemberTest {

    private static final String URL = "/api/v1/communities/{communityId}/memberships/import";
    private static final List<String> COLUMNS = List.of("personalId", "fullName", "memberNumber", "contactEmail",
            "address", "phoneNumber");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // --- AC1, AC2: the file as spreadsheets export it ---

    @Test
    @DisplayName("MEM-030 a file as Spanish Excel exports it, separated by semicolons and with a byte order mark, "
            + "registers every row as a pending membership with its data intact")
    void aSpanishExcelFile_registersEveryRow() throws Exception {
        importsEveryRowIntact(';', true);
    }

    @Test
    @DisplayName("MEM-030 a comma-separated file registers every row the same way")
    void aCommaSeparatedFile_registersEveryRow() throws Exception {
        importsEveryRowIntact(',', false);
    }

    private void importsEveryRowIntact(char separator, boolean bom) throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        List<Map<String, String>> rows = List.of(
                newRow("Begoña Íñiguez Peña", "Plaza de la Constitución 1, Luco de Jiloca"),
                newRow("José Ángel Muñoz", "Camino del Pajar, s/n"),
                newRow("Ñuria Güell", "Calle Ramón y Cajal 3"));

        importFile(loginUser(admin), community.getId(), csv(separator, bom, COLUMNS, rows))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(3))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].line").value(2))
                .andExpect(jsonPath("$.rows[1].line").value(3))
                .andExpect(jsonPath("$.rows[2].line").value(4))
                .andExpect(jsonPath("$.rows[*].outcome").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("REGISTERED"))));

        for (Map<String, String> row : rows) {
            Map<String, Object> membership = membershipRow(row.get("personalId"), community.getId());
            assertThat(membership.get("role")).isEqualTo("COMMUNITY_MEMBER");
            assertThat(membership.get("enabled")).isEqualTo(true);
            assertThat(membership.get("accepted_at")).isNull();
            assertThat(membership.get("invited_at")).isNull();
            assertThat(membership.get("invited_by")).isEqualTo(admin.getId());
            assertThat(membership.get("full_name")).isEqualTo(row.get("fullName"));
            assertThat(membership.get("member_number")).isEqualTo(Integer.valueOf(row.get("memberNumber")));
            assertThat(membership.get("contact_email")).isEqualTo(row.get("contactEmail"));
            assertThat(membership.get("address")).isEqualTo(row.get("address"));
            assertThat(membership.get("phone_number")).isEqualTo(row.get("phoneNumber"));
            assertThat(accountRow(row.get("personalId")).get("password")).isNull();
        }
    }

    // --- AC3: a DNI with an account in another community ---

    @Test
    @DisplayName("MEM-030 a DNI with an account in another community gets the same result as a new DNI, and that "
            + "account is unchanged")
    void aDniWithAnAccountElsewhere_getsTheSameResultAsANewOne() throws Exception {
        Community community = newCommunity();
        Community elsewhere = newCommunity();
        Map<String, Object> elsewhereRecord = newRecord();
        registered(loginAsAdminOf(elsewhere), elsewhere.getId(), elsewhereRecord);
        String existing = (String) elsewhereRecord.get("personalId");
        remember(existing);
        Map<String, Object> accountBefore = accountRow(existing);

        Map<String, String> newOne = newRow("Ana", "Calle Mayor 1");
        Map<String, String> existingOne = newRow("Ana", "Calle Mayor 1");
        existingOne.put("personalId", existing);

        JsonNode response = json(importFile(loginAsAdminOf(community), community.getId(),
                csv(';', true, COLUMNS, List.of(newOne, existingOne)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode forNew = response.get("rows").get(0);
        JsonNode forExisting = response.get("rows").get(1);
        assertThat(forNew.get("line").asInt()).isEqualTo(2);
        assertThat(forExisting.get("line").asInt()).isEqualTo(3);
        ((com.fasterxml.jackson.databind.node.ObjectNode) forNew).remove("line");
        ((com.fasterxml.jackson.databind.node.ObjectNode) forExisting).remove("line");
        assertThat(forExisting).isEqualTo(forNew);
        assertThat(forNew.get("outcome").asText()).isEqualTo("REGISTERED");

        assertThat(accountRow(existing)).isEqualTo(accountBefore);
        assertThat(accountCount(existing)).isEqualTo(1);
        assertThat(membershipRow(existing, community.getId()).get("full_name")).isEqualTo("Ana");
    }

    // --- AC4 and isolation: a DNI already in this community ---

    @Test
    @DisplayName("MEM-030 a DNI already in the community, and a DNI repeated in the file, are row errors with "
            + "MEMBERSHIP_ALREADY_EXISTS, and the other rows are registered")
    void aDniAlreadyInTheCommunity_andARepeatedDni_areRowErrors() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> existingRecord = newRecord();
        UUID existingUserId = registered(bearer, community.getId(), existingRecord);
        Map<String, Object> existingBefore = membershipRow((String) existingRecord.get("personalId"),
                community.getId());

        Map<String, String> first = newRow("Ana", "Calle 1");
        Map<String, String> existing = newRow("Otra Persona", "Calle 2");
        existing.put("personalId", (String) existingRecord.get("personalId"));
        Map<String, String> repeated = newRow("Luis", "Calle 3");
        Map<String, String> repeatedAgain = newRow("Luis Otra Vez", "Calle 4");
        repeatedAgain.put("personalId", repeated.get("personalId"));

        String response = importFile(bearer, community.getId(),
                csv(';', false, COLUMNS, List.of(first, existing, repeated, repeatedAgain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.rows[0].outcome").value("REGISTERED"))
                .andExpect(jsonPath("$.rows[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.rows[1].line").value(3))
                .andExpect(jsonPath("$.rows[1].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.rows[2].outcome").value("REGISTERED"))
                .andExpect(jsonPath("$.rows[3].outcome").value("FAILED"))
                .andExpect(jsonPath("$.rows[3].line").value(5))
                .andExpect(jsonPath("$.rows[3].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        // The account's id, which the registration's 409 carries, is not in a row result
        assertThat(response).doesNotContain(existingUserId.toString());
        assertThat(membershipRow((String) existingRecord.get("personalId"), community.getId()))
                .isEqualTo(existingBefore);
        assertThat(membershipRow(repeated.get("personalId"), community.getId()).get("full_name")).isEqualTo("Luis");
        assertThat(membershipCount(repeated.get("personalId"))).isEqualTo(1);
    }

    @Test
    @DisplayName("MEM-030 a row that fails to register in the middle of the file leaves the rows before and after "
            + "it registered")
    void aRowThatFailsInTheMiddle_leavesTheOthersRegistered() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> existingRecord = newRecord();
        registered(bearer, community.getId(), existingRecord);

        Map<String, String> before = newRow("Antes", "Calle 1");
        Map<String, String> failing = newRow("Falla", "Calle 2");
        failing.put("personalId", (String) existingRecord.get("personalId"));
        Map<String, String> after = newRow("Después", "Calle 3");

        importFile(bearer, community.getId(), csv(';', false, COLUMNS, List.of(before, failing, after)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.failed").value(1));

        assertThat(membershipRow(before.get("personalId"), community.getId()).get("full_name")).isEqualTo("Antes");
        assertThat(membershipRow(after.get("personalId"), community.getId()).get("full_name")).isEqualTo("Después");
    }

    // --- AC5: nothing is sent ---

    @Test
    @DisplayName("MEM-030 the import sends no invitation and issues no token, even for rows with a contact email")
    void theImport_sendsNothing() throws Exception {
        Community community = newCommunity();
        List<Map<String, String>> rows = List.of(newRow("Ana", "Calle 1"), newRow("Luis", "Calle 2"));

        importFile(loginAsAdminOf(community), community.getId(), csv(';', true, COLUMNS, rows))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2));

        verify(emailSender, never()).send(any());
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
        for (Map<String, String> row : rows) {
            assertThat(row.get("contactEmail")).isNotBlank();
            assertThat(tokenCount(row.get("personalId"))).isZero();
            assertThat(membershipRow(row.get("personalId"), community.getId()).get("invited_at")).isNull();
        }
    }

    // --- AC6: the size limit ---

    @Test
    @DisplayName("MEM-030 a file of more than 1000 rows is refused before any row is registered")
    void aFileOfMoreThan1000Rows_isRefused() throws Exception {
        Community community = newCommunity();
        List<Map<String, String>> rows = bulkRows(1001);

        importFile(loginAsAdminOf(community), community.getId(), csv(';', true, List.of("personalId", "fullName"),
                rows))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_IMPORT_TOO_MANY_ROWS"))
                .andExpect(jsonPath("$.errors[0].params.maxRows").value("1000"));

        assertThat(membershipsIn(community)).isEqualTo(1);
        assertThat(accountCount(rows.get(0).get("personalId"))).isZero();
        assertThat(accountCount(rows.get(1000).get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-030 a file of exactly 1000 rows is imported")
    void aFileOf1000Rows_isImported() throws Exception {
        Community community = newCommunity();
        List<Map<String, String>> rows = bulkRows(1000);
        String bearer = loginAsAdminOf(community);

        long start = System.nanoTime();
        importFile(bearer, community.getId(), csv(';', true, List.of("personalId", "fullName"), rows))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(1000))
                .andExpect(jsonPath("$.failed").value(0));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Importing 1000 members took " + millis + " ms");

        assertThat(membershipsIn(community)).isEqualTo(1001);
    }

    // --- AC7: who may import ---

    @Test
    @DisplayName("MEM-030 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        Community community = newCommunity();
        Map<String, String> row = newRow("Ana", "Calle 1");

        importFile(loginAsPlatformAdmin(), community.getId(), csv(';', false, COLUMNS, List.of(row)))
                .andExpect(status().isForbidden());

        assertThat(accountCount(row.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-030 a plain member of the community is forbidden")
    void aMemberOfTheCommunity_isForbidden() throws Exception {
        Community community = newCommunity();
        Map<String, String> row = newRow("Ana", "Calle 1");

        importFile(loginAsMemberOf(community), community.getId(), csv(';', false, COLUMNS, List.of(row)))
                .andExpect(status().isForbidden());

        assertThat(accountCount(row.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-030 an active admin of another community is answered as a non-member")
    void anAdminOfAnotherCommunity_isAnsweredAsANonMember() throws Exception {
        Community community = newCommunity();
        Map<String, String> row = newRow("Ana", "Calle 1");

        importFile(loginAsAdminOf(newCommunity()), community.getId(), csv(';', false, COLUMNS, List.of(row)))
                .andExpect(status().isNotFound());

        assertThat(accountCount(row.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-030 an unknown community is not found")
    void anUnknownCommunity_isNotFound() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");

        importFile(loginAsAdminOf(newCommunity()), UUID.randomUUID(), csv(';', false, COLUMNS, List.of(row)))
                .andExpect(status().isNotFound());

        assertThat(accountCount(row.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-030 an anonymous caller is unauthorized")
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        Map<String, String> row = newRow("Ana", "Calle 1");

        mockMvc.perform(multipart(URL, community.getId())
                        .file(new MockMultipartFile("file", "members.csv", "text/csv",
                                csv(';', false, COLUMNS, List.of(row)))))
                .andExpect(status().isUnauthorized());

        assertThat(accountCount(row.get("personalId"))).isZero();
    }

    // --- Whole-file refusals ---

    @Test
    @DisplayName("MEM-030 a file naming an unknown column, such as the old import's password, is refused and "
            + "nothing is registered")
    void anUnknownHeader_isRefused() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");
        row.put("password", "Secret-123");
        List<String> columns = new ArrayList<>(COLUMNS);
        columns.add("password");

        assertRefused(csv(';', true, columns, List.of(row)), "MEMBERSHIP_IMPORT_UNKNOWN_HEADERS", row)
                .andExpect(jsonPath("$.errors[0].params.headers").value("password"));
    }

    @Test
    @DisplayName("MEM-030 a file without the fullName column is refused and nothing is registered")
    void aMissingFullNameHeader_isRefused() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");

        assertRefused(csv(';', true, List.of("personalId", "address"), List.of(row)),
                "MEMBERSHIP_IMPORT_MISSING_HEADERS", row)
                .andExpect(jsonPath("$.errors[0].params.headers").value("fullName"));
    }

    @Test
    @DisplayName("MEM-030 a file naming a column twice is refused and nothing is registered")
    void aDuplicatedHeader_isRefused() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");

        assertRefused(csv(';', true, List.of("personalId", "fullName", "fullName"), List.of(row)),
                "MEMBERSHIP_IMPORT_DUPLICATED_HEADERS", row)
                .andExpect(jsonPath("$.errors[0].params.headers").value("fullName"));
    }

    @Test
    @DisplayName("MEM-030 a file that is not valid UTF-8 is refused and nothing is registered")
    void invalidUtf8_isRefused() throws Exception {
        Map<String, String> row = newRow("Peña", "Calle 1");
        // As Excel saves "CSV (separated by semicolons)" on Windows
        byte[] windows1252 = new String(csv(';', false, COLUMNS, List.of(row)), StandardCharsets.UTF_8)
                .getBytes(java.nio.charset.Charset.forName("windows-1252"));

        assertRefused(windows1252, "MEMBERSHIP_IMPORT_INVALID_ENCODING", row);
    }

    @Test
    @DisplayName("MEM-030 a header line with both separators is refused and nothing is registered")
    void anAmbiguousSeparator_isRefused() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");
        String file = "personalId;fullName,address\n" + row.get("personalId") + ";Ana,Calle\n";

        assertRefused(file.getBytes(StandardCharsets.UTF_8), "MEMBERSHIP_IMPORT_AMBIGUOUS_SEPARATOR", row);
    }

    @Test
    @DisplayName("MEM-030 a file that is not valid CSV is refused and nothing is registered")
    void aMalformedFile_isRefused() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");
        String file = "personalId;fullName\n" + row.get("personalId") + ";\"Ana\n";

        assertRefused(file.getBytes(StandardCharsets.UTF_8), "MEMBERSHIP_IMPORT_MALFORMED_FILE", row);
    }

    @Test
    @DisplayName("MEM-030 a file without a header line is refused, and the member data read as its headers is not "
            + "logged")
    void aFileWithoutAHeaderLine_logsNoneOfItsData() throws Exception {
        Map<String, String> row = newRow("Ana", "Calle 1");
        String file = row.get("personalId") + ";Ana;" + row.get("contactEmail") + "\n";

        assertRefused(file.getBytes(StandardCharsets.UTF_8), "MEMBERSHIP_IMPORT_UNKNOWN_HEADERS", row);

        // The base class checks every line for the remembered email and personal id; this checks the line exists
        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member import refused"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).endsWith("reason=UNKNOWN_HEADERS"));
    }

    // --- Row validation ---

    @Test
    @DisplayName("MEM-030 rows that break a rule of the registration are row errors naming the column but not the "
            + "value, and the other rows are registered")
    void invalidRows_areRowErrors() throws Exception {
        Community community = newCommunity();
        Map<String, String> valid = newRow("Ana", "Calle 1");
        Map<String, String> blankName = newRow("   ", "Calle 2");
        Map<String, String> negativeNumber = newRow("Luis", "Calle 3");
        negativeNumber.put("memberNumber", "-1");
        Map<String, String> invalidEmail = newRow("Marta", "Calle 4");
        String typedEmail = "marta-" + UUID.randomUUID() + "-at-nowhere";
        remember(typedEmail);
        invalidEmail.put("contactEmail", typedEmail);
        Map<String, String> emptyPersonalId = newRow("Pedro", "Calle 5");
        emptyPersonalId.put("personalId", " .-. ");
        Map<String, String> blankEmail = newRow("Rosa", "Calle 6");
        blankEmail.put("contactEmail", " \t ");

        String response = importFile(loginAsAdminOf(community), community.getId(), csv(';', true, COLUMNS,
                List.of(valid, blankName, negativeNumber, invalidEmail, emptyPersonalId, blankEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.failed").value(4))
                .andExpect(jsonPath("$.rows[0].outcome").value("REGISTERED"))
                .andExpect(jsonPath("$.rows[1].code").value("MEMBERSHIP_IMPORT_ROW_INVALID"))
                .andExpect(jsonPath("$.rows[1].message").value(org.hamcrest.Matchers.containsString("fullName")))
                .andExpect(jsonPath("$.rows[2].code").value("MEMBERSHIP_IMPORT_ROW_INVALID"))
                .andExpect(jsonPath("$.rows[2].message").value(org.hamcrest.Matchers.containsString("memberNumber")))
                .andExpect(jsonPath("$.rows[3].code").value("MEMBERSHIP_IMPORT_ROW_INVALID"))
                .andExpect(jsonPath("$.rows[3].message").value(org.hamcrest.Matchers.containsString("contactEmail")))
                .andExpect(jsonPath("$.rows[4].code").value("MEMBERSHIP_IMPORT_ROW_INVALID"))
                .andExpect(jsonPath("$.rows[4].message").value(org.hamcrest.Matchers.containsString("personalId")))
                .andExpect(jsonPath("$.rows[5].outcome").value("REGISTERED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(typedEmail).doesNotContain("-1");
        assertThat(accountCount(blankName.get("personalId"))).isZero();
        assertThat(accountCount(negativeNumber.get("personalId"))).isZero();
        assertThat(accountCount(invalidEmail.get("personalId"))).isZero();
        assertThat(accountCount("")).isZero();
        assertThat(membershipRow(valid.get("personalId"), community.getId()).get("full_name")).isEqualTo("Ana");
        // A whitespace-only contact email is no email, on the record and on the account
        assertThat(membershipRow(blankEmail.get("personalId"), community.getId()).get("contact_email")).isNull();
        assertThat(accountRow(blankEmail.get("personalId")).get("email")).isNull();
    }

    @Test
    @DisplayName("MEM-030 a row without as many cells as the header is a row error, and the other rows are registered")
    void aRowWithTheWrongNumberOfCells_isARowError() throws Exception {
        Community community = newCommunity();
        Map<String, String> valid = newRow("Ana", "Calle 1");
        String file = "personalId;fullName\n" + valid.get("personalId") + ";Ana\nonly-one-cell\n";

        importFile(loginAsAdminOf(community), community.getId(), file.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].outcome").value("REGISTERED"))
                .andExpect(jsonPath("$.rows[1].line").value(3))
                .andExpect(jsonPath("$.rows[1].code").value("MEMBERSHIP_IMPORT_ROW_MALFORMED"))
                .andExpect(jsonPath("$.rows[1].message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("only-one-cell"))));
    }

    // --- Response and logging ---

    @Test
    @DisplayName("MEM-030 a row result carries exactly its line, outcome, code and message")
    void aRowResult_hasExactlyItsFields() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> existingRecord = newRecord();
        registered(bearer, community.getId(), existingRecord);
        Map<String, String> existing = newRow("Ana", "Calle 1");
        existing.put("personalId", (String) existingRecord.get("personalId"));

        JsonNode response = json(importFile(bearer, community.getId(),
                csv(';', false, COLUMNS, List.of(newRow("Luis", "Calle 2"), existing)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("registered", "failed", "rows");
        for (JsonNode row : response.get("rows")) {
            assertThat(fieldNames(row)).containsExactlyInAnyOrder("line", "outcome", "code", "message");
        }
        assertThat(response.get("rows").get(0).get("code").isNull()).isTrue();
        assertThat(response.get("rows").get(0).get("message").isNull()).isTrue();
    }

    @Test
    @DisplayName("MEM-030 one line is logged per import, with the community and the counts, and nothing personal")
    void oneLineIsLoggedPerImport() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> existingRecord = newRecord();
        registered(bearer, community.getId(), existingRecord);
        Map<String, String> existing = newRow("Ana", "Calle 1");
        existing.put("personalId", (String) existingRecord.get("personalId"));
        Map<String, String> fresh = newRow("Luis", "Calle 2");
        logs.clear();

        importFile(bearer, community.getId(), csv(';', false, COLUMNS, List.of(fresh, existing)))
                .andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Members imported"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Members imported: communityId="
                            + community.getId() + ", registered=1, failed=1");
                });
        // The registration's own line, with the masked personal id, is the only line per row
        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member registered"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo("Member registered: personalId="
                        + UserPersonalId.mask(fresh.get("personalId")) + ", communityId=" + community.getId()));
    }

    // --- Helpers ---

    /**
     * A row with every column filled, a fresh personal id and contact email, and the given name and address. Its
     * personal id and email must never be logged, and its account is deleted after the test.
     */
    private Map<String, String> newRow(String fullName, String address) {
        String personalId = UserMother.randomPersonalId();
        remember(personalId);
        deleteAfterwards(personalId);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("personalId", personalId);
        row.put("fullName", fullName);
        row.put("memberNumber", String.valueOf(1 + new java.util.Random().nextInt(9999)));
        row.put("contactEmail", newEmail());
        row.put("address", address);
        row.put("phoneNumber", "600 000 000");
        return row;
    }

    /**
     * Rows with only a personal id and a name, which are not remembered as secrets: checking a thousand of them
     * against every log line would make the test slow without making it stronger.
     */
    private List<Map<String, String>> bulkRows(int count) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String personalId = UserMother.randomPersonalId();
            deleteAfterwards(personalId);
            rows.add(Map.of("personalId", personalId, "fullName", "Socio " + i));
        }
        return rows;
    }

    /**
     * A file with a header line of {@code columns} and a line per row, values quoted only when they need it, lines
     * ended with CRLF as spreadsheets end them.
     */
    private static byte[] csv(char separator, boolean bom, List<String> columns, List<Map<String, String>> rows) {
        StringBuilder text = new StringBuilder(String.join(String.valueOf(separator), columns)).append("\r\n");
        for (Map<String, String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String column : columns) {
                cells.add(quoted(row.getOrDefault(column, ""), separator));
            }
            text.append(String.join(String.valueOf(separator), cells)).append("\r\n");
        }
        byte[] content = text.toString().getBytes(StandardCharsets.UTF_8);
        if (!bom) {
            return content;
        }
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        return withBom;
    }

    private static String quoted(String value, char separator) {
        return value.indexOf(separator) >= 0 || value.indexOf('"') >= 0
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private ResultActions importFile(String bearer, UUID communityId, byte[] content) throws Exception {
        return mockMvc.perform(multipart(URL, communityId)
                .file(new MockMultipartFile("file", "socios.csv", "text/csv", content))
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions assertRefused(byte[] content, String code, Map<String, String> row) throws Exception {
        Community community = newCommunity();
        ResultActions result = importFile(loginAsAdminOf(community), community.getId(), content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(code));
        assertThat(accountCount(row.get("personalId"))).isZero();
        assertThat(membershipsIn(community)).isEqualTo(1);
        return result;
    }

    private int membershipsIn(Community community) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships WHERE community_id = ?",
                Integer.class, community.getId());
    }

    private JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
