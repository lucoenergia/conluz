package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.SurplusClaimTestData.PlantWithSupplies;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.SurplusClaimTestData.MADRID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The claim file end to end over a real PostgreSQL and InfluxDB, compared with the preview of the same month. The
 * clock is at 2026-11-10 in Madrid.
 *
 * <p>The plant has two supplies in scope, each with a record at every hour of March, September and October 2026,
 * except: on 5 October at 03:00 neither has one, so that hour is null; on 6 October at 04:00 only the second misses
 * it, so the hour is 0. Both days are estimated, the others real. On 10 October at noon the supplies store 12.3456
 * and 1 kWh, and at the first 02:00 of 25 October the first stores 2.5 kWh.
 */
class GetPlantSurplusClaimFileControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/surplus-claim/file";
    private static final String PREVIEW_PATH = "/api/v1/plants/{plantId}/surplus-claim";
    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDateTime NULL_HOUR = LocalDateTime.of(2026, 10, 5, 3, 0);
    private static final LocalDateTime ZERO_HOUR = LocalDateTime.of(2026, 10, 6, 4, 0);
    private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);
    private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);
    private static final String HEADER = "fecha;estado;HOR1;HOR2;HOR3;HOR4;HOR5;HOR6;HOR7;HOR8;HOR9;HOR10;HOR11;HOR12;"
            + "HOR13;HOR14;HOR15;HOR16;HOR17;HOR18;HOR19;HOR20;HOR21;HOR22;HOR23;HOR24;HOR25";
    private static final int DATE_COLUMN = 3;
    private static final int FIRST_HOUR_COLUMN = 4;

    @Autowired
    private CommunityEnergyTestFixture fixture;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private GetPlantSurplusClaimService getPlantSurplusClaimService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    private final List<UserId> platformAdmins = new ArrayList<>();

    private SurplusClaimTestData data;

    @BeforeEach
    void setUp() {
        travelTo(NOW);
        data = new SurplusClaimTestData(fixture, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository);
    }

    @AfterEach
    void tearDown() {
        platformAdmins.forEach(managePlatformAdminRepository::revoke);
        data.clear();
    }

    @Test
    @DisplayName("SCL-017 SCL-018 AC1 a 30-day month is a ZIP holding the template with 30 Carga rows: the CIL, AS, the state and the date")
    void aThirtyDayMonthIsAZipWithThirtyRows() throws Exception {
        String cil = randomCil();
        PlantWithSupplies plant = claimedPlant(cil);

        MockHttpServletResponse response = file(plant, "2026-09", "ENDESA").andExpect(status().isOk())
                .andReturn().getResponse();

        assertEquals("application/zip", response.getContentType());
        assertEquals("reclamacionMedidas_" + cil + "_2026-09.zip", filename(response));
        try (XSSFWorkbook workbook = workbook(response, "reclamacionMedidas_" + cil + "_2026-09.xlsx")) {
            assertEquals(List.of("Instrucciones", "Carga", "Hoja3"),
                    List.of(workbook.getSheetName(0), workbook.getSheetName(1), workbook.getSheetName(2)));
            XSSFSheet carga = workbook.getSheet("Carga");
            for (int day = 1; day <= 30; day++) {
                XSSFRow row = carga.getRow(day);
                assertEquals(cil, row.getCell(0).getStringCellValue());
                assertEquals("AS", row.getCell(1).getStringCellValue());
                assertEquals("R", row.getCell(2).getStringCellValue());
                assertEquals(SEPTEMBER.atDay(day).atStartOfDay(), row.getCell(DATE_COLUMN).getLocalDateTimeCellValue());
                assertEquals("dd/mm/yyyy", row.getCell(DATE_COLUMN).getCellStyle().getDataFormatString());
            }
            assertNoValue(carga.getRow(31));
            assertEquals(List.of("B2:B31", "C2:C31"), validatedRanges(carga));
        }
    }

    @Test
    @DisplayName("SCL-017 AC5 a 31-day month fills rows 2 to 32 and its validations cover them all")
    void aThirtyOneDayMonthIsValidatedToItsLastRow() throws Exception {
        String cil = randomCil();
        PlantWithSupplies plant = claimedPlant(cil);

        MockHttpServletResponse response = file(plant, "2026-10", "ENDESA").andExpect(status().isOk())
                .andReturn().getResponse();

        try (XSSFWorkbook workbook = workbook(response, "reclamacionMedidas_" + cil + "_2026-10.xlsx")) {
            XSSFSheet carga = workbook.getSheet("Carga");
            for (int day = 1; day <= 31; day++) {
                XSSFRow row = carga.getRow(day);
                assertEquals(cil, row.getCell(0).getStringCellValue());
                assertEquals(day == 5 || day == 6 ? "E" : "R", row.getCell(2).getStringCellValue(), "day " + day);
                assertEquals(OCTOBER.atDay(day).atStartOfDay(), row.getCell(DATE_COLUMN).getLocalDateTimeCellValue());
            }
            assertEquals(List.of("B2:B32", "C2:C32"), validatedRanges(carga));
        }
    }

    @Test
    @DisplayName("SCL-017 SCL-019 AC2 AC3 an hour is rounded to 2 decimals in Endesa and 3 in the CSV, a null hour is 0 and empty on an E day")
    void theHoursAreRoundedAndANullHourWrittenPerFormat() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());

        try (XSSFWorkbook workbook = workbook(file(plant, "2026-10", "ENDESA").andExpect(status().isOk())
                .andReturn().getResponse(), null)) {
            XSSFSheet carga = workbook.getSheet("Carga");
            assertEquals(13.35, hour(carga, LocalDate.of(2026, 10, 10), 12).getNumericCellValue());
            XSSFCell nullHour = hour(carga, NULL_HOUR.toLocalDate(), 3);
            assertEquals(CellType.NUMERIC, nullHour.getCellType());
            assertEquals(0.0, nullHour.getNumericCellValue());
            assertEquals("E", carga.getRow(5).getCell(2).getStringCellValue());
        }

        List<String[]> csv = csv(file(plant, "2026-10", "GENERIC").andExpect(status().isOk()).andReturn()
                .getResponse());
        assertEquals("13,346", csv.get(10)[2 + 12]);
        assertEquals("E", csv.get(5)[1]);
        assertEquals("", csv.get(5)[2 + 3]);
        assertEquals("0,000", csv.get(5)[2 + 2]);
        assertEquals("E", csv.get(6)[1]);
        assertEquals("0,000", csv.get(6)[2 + 4]);
    }

    @Test
    @DisplayName("SCL-017 SCL-019 AC4 the 23-hour day fills HOR1 to HOR23 and the 25-hour day HOR1 to HOR25, in both formats")
    void theDaysTheClocksChangeHaveTheirHours() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());

        try (XSSFWorkbook march = workbook(file(plant, "2026-03", "ENDESA").andReturn().getResponse(), null);
             XSSFWorkbook october = workbook(file(plant, "2026-10", "ENDESA").andReturn().getResponse(), null)) {
            for (int hour = 0; hour < 25; hour++) {
                assertEquals(hour < 23 ? CellType.NUMERIC : CellType.BLANK,
                        hour(march.getSheet("Carga"), SPRING_FORWARD, hour).getCellType(), "HOR" + (hour + 1));
                assertEquals(CellType.NUMERIC, hour(october.getSheet("Carga"), FALL_BACK, hour).getCellType(),
                        "HOR" + (hour + 1));
            }
            assertEquals(2.5, hour(october.getSheet("Carga"), FALL_BACK, 2).getNumericCellValue());
            assertEquals(CellType.BLANK, hour(october.getSheet("Carga"), FALL_BACK.plusDays(1), 24).getCellType());
        }

        List<String[]> march = csv(file(plant, "2026-03", "GENERIC").andReturn().getResponse());
        List<String[]> october = csv(file(plant, "2026-10", "GENERIC").andReturn().getResponse());
        for (int hour = 0; hour < 25; hour++) {
            assertEquals(hour >= 23, march.get(SPRING_FORWARD.getDayOfMonth())[2 + hour].isEmpty(), "HOR" + (hour + 1));
            assertFalse(october.get(FALL_BACK.getDayOfMonth())[2 + hour].isEmpty(), "HOR" + (hour + 1));
        }
        assertEquals("2,500", october.get(FALL_BACK.getDayOfMonth())[2 + 2]);
        Stream.concat(march.stream().skip(1), october.stream().skip(1))
                .forEach(fields -> assertEquals(27, fields.length, String.join(";", fields)));
    }

    @Test
    @DisplayName("SCL-014 AC10 every day, state and hour of both files is the preview's for the month, rounded")
    void everyValueIsThePreviews() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());
        String admin = loginAsCommunityAdmin(plant.communityId());
        JsonNode preview = objectMapper.readTree(request(get(PREVIEW_PATH, plant.plant().getId())
                .header(HttpHeaders.AUTHORIZATION, admin).queryParam("month", "2026-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        PlantSurplusClaim claim = getPlantSurplusClaimService.getSurplusClaim(plant.plant().getId(), OCTOBER);

        List<String[]> csv = csv(file(plant, "2026-10", "GENERIC").andReturn().getResponse());
        try (XSSFWorkbook workbook = workbook(file(plant, "2026-10", "ENDESA").andReturn().getResponse(), null)) {
            XSSFSheet carga = workbook.getSheet("Carga");
            JsonNode days = preview.path("days");
            assertEquals(days.size(), csv.size() - 1);
            for (int day = 0; day < days.size(); day++) {
                JsonNode previewDay = days.get(day);
                SurplusClaimDay claimDay = claim.days().get(day);
                String[] fields = csv.get(day + 1);
                XSSFRow row = carga.getRow(day + 1);
                String state = previewDay.path("state").asText().equals("REAL") ? "R" : "E";
                assertEquals(LocalDate.parse(previewDay.path("date").asText()), claimDay.date());
                assertEquals(claimDay.date().atStartOfDay(), row.getCell(DATE_COLUMN).getLocalDateTimeCellValue());
                assertEquals(state, fields[1]);
                assertEquals(state, row.getCell(2).getStringCellValue());
                JsonNode hours = previewDay.path("hours");
                for (int hour = 0; hour < 25; hour++) {
                    XSSFCell cell = row.getCell(FIRST_HOUR_COLUMN + hour);
                    if (hour >= hours.size()) {
                        assertEquals("", fields[2 + hour]);
                        assertEquals(CellType.BLANK, cell.getCellType());
                        continue;
                    }
                    JsonNode previewValue = hours.get(hour).path("surplusKWh");
                    Double unrounded = claimDay.hours().get(hour).surplusKWh();
                    if (previewValue.isNull()) {
                        assertNull(unrounded);
                        assertEquals("", fields[2 + hour]);
                        assertEquals(0.0, cell.getNumericCellValue());
                    } else {
                        assertEquals(previewValue.decimalValue().setScale(3, RoundingMode.HALF_UP).toPlainString()
                                .replace('.', ','), fields[2 + hour]);
                        assertEquals(BigDecimal.valueOf(unrounded).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                                cell.getNumericCellValue());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("SCL-016 AC6 without a CIL the Endesa file is refused with 409 SURPLUS_CLAIM_CIL_REQUIRED and the generic one is returned")
    void withoutACilOnlyTheGenericFileIsReturned() throws Exception {
        PlantWithSupplies plant = claimedPlant(null);

        file(plant, "2026-09", "ENDESA")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SURPLUS_CLAIM_CIL_REQUIRED"));
        MockHttpServletResponse generic = file(plant, "2026-09", "GENERIC").andExpect(status().isOk())
                .andReturn().getResponse();
        assertEquals("excedentes_" + plant.plant().getRegulatoryCode() + "_2026-09.csv", filename(generic));
    }

    @Test
    @DisplayName("SCL-015 SCL-014 AC7 no month, no format or an unknown format is a bad request, and the current month or a later one is SURPLUS_CLAIM_MONTH_NOT_CLOSED")
    void theMonthAndTheFormatAreChecked() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());
        String admin = loginAsCommunityAdmin(plant.communityId());
        UUID plantId = plant.plant().getId();

        request(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, admin).queryParam("format", "GENERIC"))
                .andExpect(status().isBadRequest());
        request(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, admin).queryParam("month", "2026-09"))
                .andExpect(status().isBadRequest());
        for (String format : new String[]{"IBERDROLA", ""}) {
            file(plantId, admin, "2026-09", format).andExpect(status().isBadRequest());
        }
        file(plantId, admin, "2026-13", "GENERIC").andExpect(status().isBadRequest());
        for (String month : new String[]{"2026-11", "2027-02"}) {
            for (String format : new String[]{"ENDESA", "GENERIC"}) {
                file(plantId, admin, month, format)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors[0].code").value("SURPLUS_CLAIM_MONTH_NOT_CLOSED"));
            }
        }
    }

    @Test
    @DisplayName("SCL-020 AC8 a month with warnings still gets both files")
    void aMonthWithWarningsGetsBothFiles() throws Exception {
        String cil = randomCil();
        PlantWithSupplies plant = claimedPlant(cil);
        String admin = loginAsCommunityAdmin(plant.communityId());
        request(get(PREVIEW_PATH, plant.plant().getId()).header(HttpHeaders.AUTHORIZATION, admin)
                .queryParam("month", "2024-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.noAppliedCoefficients").value(true));
        request(get(PREVIEW_PATH, plant.plant().getId()).header(HttpHeaders.AUTHORIZATION, admin)
                .queryParam("month", "2026-10"))
                .andExpect(jsonPath("$.warnings.suppliesWithGaps[0]").exists());

        for (String month : new String[]{"2024-03", "2026-10"}) {
            file(plant, month, "ENDESA").andExpect(status().isOk());
            file(plant, month, "GENERIC").andExpect(status().isOk());
        }
        List<String[]> csv = csv(file(plant, "2024-03", "GENERIC").andReturn().getResponse());
        assertEquals(32, csv.size());
        csv.stream().skip(1).forEach(fields -> assertEquals("E", fields[1]));
    }

    @Test
    @DisplayName("SCL-013 AC9 a community admin downloads it, a member gets 403, anyone else 404, an unknown plant 404, and no token 401")
    void onlyACommunityAdminDownloadsIt() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());
        UUID plantId = plant.plant().getId();
        User member = fixture.createMember(plant.communityId());

        for (String format : new String[]{"ENDESA", "GENERIC"}) {
            file(plantId, loginAsCommunityAdmin(plant.communityId()), "2026-09", format).andExpect(status().isOk());
            file(plantId, loginUser(member), "2026-09", format).andExpect(status().isForbidden());
            file(plantId, loginAsPartner(), "2026-09", format).andExpect(status().isNotFound());
            file(plantId, loginAsPlatformAdminWithoutMembership(), "2026-09", format).andExpect(status().isNotFound());
            file(UUID.randomUUID(), loginAsCommunityAdmin(plant.communityId()), "2026-09", format)
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(PATH, plantId).queryParam("month", "2026-09").queryParam("format", format))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("SCL-019 AC11 the CSV is text/csv in UTF-8 with a BOM, CRLF line ends and the exact header, named after the regulatory code")
    void theCsvStartsWithABomAndTheHeader() throws Exception {
        PlantWithSupplies plant = claimedPlant(randomCil());

        MockHttpServletResponse response = file(plant, "2026-09", "GENERIC").andExpect(status().isOk())
                .andReturn().getResponse();

        assertEquals("text/csv;charset=UTF-8", response.getContentType());
        assertEquals("excedentes_" + plant.plant().getRegulatoryCode() + "_2026-09.csv", filename(response));
        byte[] content = response.getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(content, 3));
        String text = new String(content, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿" + HEADER + "\r\n"));
        assertTrue(text.endsWith("\r\n"));
        assertEquals(text.split("\n", -1).length, text.split("\r\n", -1).length);
        assertEquals(31, text.split("\r\n").length);
    }

    /**
     * Two supplies in scope with coefficients of 0.5 since 2025-01-01, and a third supply of the community without
     * any, with the records the class describes.
     */
    private PlantWithSupplies claimedPlant(String cil) {
        PlantWithSupplies plant = data.plantWithSupplies(3, cil);
        SharingAgreement agreement = data.agreement(plant.plant());
        data.coefficient(plant.first(), plant.plant(), agreement, "0.5", LocalDate.of(2025, 1, 1), null);
        data.coefficient(plant.second(), plant.plant(), agreement, "0.5", LocalDate.of(2025, 1, 1), null);
        for (YearMonth month : List.of(MARCH, SEPTEMBER, OCTOBER)) {
            zeroSurplusEveryHourBut(plant.first(), month, Set.of(NULL_HOUR));
            zeroSurplusEveryHourBut(plant.second(), month, Set.of(NULL_HOUR, ZERO_HOUR));
        }
        data.surplus(plant.first(), LocalDateTime.of(2026, 10, 10, 12, 0), 12.3456f);
        data.surplus(plant.second(), LocalDateTime.of(2026, 10, 10, 12, 0), 1f);
        data.surplus(plant.first(), LocalDateTime.of(2026, 10, 25, 2, 0), 2.5f);
        return plant;
    }

    private void zeroSurplusEveryHourBut(Supply supply, YearMonth month, Set<LocalDateTime> skipped) {
        Set<Instant> skippedInstants = Set.copyOf(skipped.stream()
                .map(hour -> hour.atZone(MADRID).toInstant())
                .toList());
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(MADRID).toInstant();
        List<Instant> hours = Stream.iterate(month.atDay(1).atStartOfDay(MADRID).toInstant(),
                        hour -> hour.isBefore(end), hour -> hour.plus(Duration.ofHours(1)))
                .filter(hour -> !skippedInstants.contains(hour))
                .toList();
        fixture.writeConsumption(supply, hours, 0.1f, 0f, 0f);
    }

    private static String randomCil() {
        return RandomStringUtils.random(25, true, true).toUpperCase();
    }

    private static XSSFCell hour(XSSFSheet carga, LocalDate date, int hour) {
        return carga.getRow(date.getDayOfMonth()).getCell(FIRST_HOUR_COLUMN + hour);
    }

    private static void assertNoValue(XSSFRow row) {
        if (row != null) {
            row.forEach(cell -> assertEquals(CellType.BLANK, cell.getCellType()));
        }
    }

    private static List<String> validatedRanges(XSSFSheet carga) {
        return carga.getDataValidations().stream()
                .flatMap(validation -> Arrays.stream(validation.getRegions().getCellRangeAddresses()))
                .map(CellRangeAddress::formatAsString)
                .sorted()
                .toList();
    }

    private static String filename(MockHttpServletResponse response) {
        ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(disposition.isAttachment());
        return disposition.getFilename();
    }

    /**
     * The only entry of the ZIP, opened as a workbook; its name is checked when one is given.
     */
    private static XSSFWorkbook workbook(MockHttpServletResponse response, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            if (entryName != null) {
                assertEquals(entryName, entry.getName());
            }
            byte[] workbook = zip.readAllBytes();
            assertNull(zip.getNextEntry());
            return new XSSFWorkbook(new ByteArrayInputStream(workbook));
        }
    }

    /**
     * The CSV's lines, header first, each split into its fields with the empty ones kept.
     */
    private static List<String[]> csv(MockHttpServletResponse response) {
        String text = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).substring(1);
        return Arrays.stream(text.split("\r\n")).map(line -> line.split(";", -1)).toList();
    }

    private ResultActions file(PlantWithSupplies plant, String month, String format) throws Exception {
        return file(plant.plant().getId(), loginAsCommunityAdmin(plant.communityId()), month, format);
    }

    private ResultActions file(UUID plantId, String token, String month, String format) throws Exception {
        return request(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, token).queryParam("month", month)
                .queryParam("format", format));
    }

    private ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    /**
     * A platform admin with a personal ID of their own, revoked after the test. This test commits what it creates:
     * the default admin would stay in the database, where other tests create a user with its personal ID, and a
     * platform admin left behind would be counted by the tests that count them.
     */
    private String loginAsPlatformAdminWithoutMembership() throws Exception {
        User platformAdmin = UserMother.randomUser();
        platformAdmin.enable();
        createUserRepository.create(platformAdmin);
        managePlatformAdminRepository.grant(UserId.of(platformAdmin.getId()));
        platformAdmins.add(UserId.of(platformAdmin.getId()));
        return loginUser(platformAdmin);
    }
}
