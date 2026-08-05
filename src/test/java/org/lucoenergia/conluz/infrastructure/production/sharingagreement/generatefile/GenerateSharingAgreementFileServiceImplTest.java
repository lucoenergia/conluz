package org.lucoenergia.conluz.infrastructure.production.sharingagreement.generatefile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMissingRegulatoryCodeException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GeneratedDistributorFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateSharingAgreementFileServiceImplTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_ID = UUID.randomUUID();
    private static final String REGULATORY_CODE = "ES0031300325733001FH0FA000";
    private static final String CUPS_1 = "ES0031300325733001FH0F";
    private static final String CUPS_2 = "ES0031300325733002FH0F";
    private static final String CUPS_3 = "ES0031300325733003FH0F";

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetPlantService getPlantService;

    private GenerateSharingAgreementFileServiceImpl service() {
        return new GenerateSharingAgreementFileServiceImpl(getSharingAgreementService, getCoefficientRepository,
                getSupplyRepository, getPlantService);
    }

    private void stubAgreementAndPlant() {
        SharingAgreement agreement = new SharingAgreement.Builder().withId(AGREEMENT_ID).withPlantId(PLANT_ID).build();
        when(getSharingAgreementService.findById(AGREEMENT_ID)).thenReturn(agreement);
        Plant plant = new Plant.Builder().withId(PLANT_ID).withRegulatoryCode(REGULATORY_CODE).build();
        lenient().when(getPlantService.findById(PlantId.of(PLANT_ID))).thenReturn(plant);
    }

    private SupplyPartitionCoefficient coefficient(UUID supplyId, BigDecimal value, Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(AGREEMENT_ID)
                .withCoefficient(value)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build();
    }

    private Supply supply(UUID id, String code) {
        return new Supply.Builder().withId(id).withCode(code).build();
    }

    @Test
    void generate_throwsSharingAgreementNotFoundException_whenAgreementBelongsToDifferentPlant() {
        SharingAgreement agreement = new SharingAgreement.Builder().withId(AGREEMENT_ID).withPlantId(UUID.randomUUID()).build();
        when(getSharingAgreementService.findById(AGREEMENT_ID)).thenReturn(agreement);

        assertThrows(SharingAgreementNotFoundException.class, () -> service().generate(PLANT_ID, AGREEMENT_ID, 2023));
    }

    @Test
    void generate_throwsPlantMissingRegulatoryCodeException_whenRegulatoryCodeIsNull() {
        SharingAgreement agreement = new SharingAgreement.Builder().withId(AGREEMENT_ID).withPlantId(PLANT_ID).build();
        when(getSharingAgreementService.findById(AGREEMENT_ID)).thenReturn(agreement);
        Plant plant = new Plant.Builder().withId(PLANT_ID).build();
        when(getPlantService.findById(PlantId.of(PLANT_ID))).thenReturn(plant);

        assertThrows(PlantMissingRegulatoryCodeException.class, () -> service().generate(PLANT_ID, AGREEMENT_ID, 2023));
    }

    @Test
    void generate_throwsPlantMissingRegulatoryCodeException_whenRegulatoryCodeIsBlank() {
        SharingAgreement agreement = new SharingAgreement.Builder().withId(AGREEMENT_ID).withPlantId(PLANT_ID).build();
        when(getSharingAgreementService.findById(AGREEMENT_ID)).thenReturn(agreement);
        Plant plant = new Plant.Builder().withId(PLANT_ID).withRegulatoryCode("  ").build();
        when(getPlantService.findById(PlantId.of(PLANT_ID))).thenReturn(plant);

        assertThrows(PlantMissingRegulatoryCodeException.class, () -> service().generate(PLANT_ID, AGREEMENT_ID, 2023));
    }

    @Test
    void generate_throwsSumInvalidException_whenCoefficientSetIsEmpty() {
        stubAgreementAndPlant();
        when(getCoefficientRepository.findAllBySharingAgreementId(AGREEMENT_ID)).thenReturn(List.of());

        SharingAgreementCoefficientSumInvalidException e = assertThrows(SharingAgreementCoefficientSumInvalidException.class,
                () -> service().generate(PLANT_ID, AGREEMENT_ID, 2023));
        assertEquals(0, BigDecimal.ZERO.compareTo(e.getActualSum()));
    }

    @Test
    void generate_throwsSumInvalidException_whenSumDeviatesEvenWithinOldTolerance() {
        stubAgreementAndPlant();
        UUID supplyId1 = UUID.randomUUID();
        UUID supplyId2 = UUID.randomUUID();
        // Sums to 1.00005 -- would have passed the old 0.0001-tolerance warning check, but must
        // still be rejected now that generation uses the parser's strict 1.000000 equality.
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(supplyId1, new BigDecimal("0.500025"), null, null),
                coefficient(supplyId2, new BigDecimal("0.500025"), null, null));
        when(getCoefficientRepository.findAllBySharingAgreementId(AGREEMENT_ID)).thenReturn(coefficients);

        assertThrows(SharingAgreementCoefficientSumInvalidException.class,
                () -> service().generate(PLANT_ID, AGREEMENT_ID, 2023));
    }

    @Test
    void generate_excludesClosedCoefficients_fromSumAndLines() {
        stubAgreementAndPlant();
        UUID supplyId1 = UUID.randomUUID();
        UUID supplyId2 = UUID.randomUUID();
        UUID closedSupplyId = UUID.randomUUID();
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(supplyId1, new BigDecimal("0.500000"), Instant.now(), null),
                coefficient(supplyId2, new BigDecimal("0.500000"), Instant.now(), null),
                coefficient(closedSupplyId, new BigDecimal("0.500000"), Instant.now().minusSeconds(3600), Instant.now()));
        when(getCoefficientRepository.findAllBySharingAgreementId(AGREEMENT_ID)).thenReturn(coefficients);
        when(getSupplyRepository.findAllByIds(Set.of(supplyId1, supplyId2)))
                .thenReturn(List.of(supply(supplyId1, CUPS_1), supply(supplyId2, CUPS_2)));

        GeneratedDistributorFile file = service().generate(PLANT_ID, AGREEMENT_ID, 2023);

        String expected = CUPS_1 + ";0,500000\n" + CUPS_2 + ";0,500000\n";
        assertEquals(expected, new String(file.getContent(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void generate_includesPendingCoefficients_withNullValidFrom() {
        stubAgreementAndPlant();
        UUID supplyId1 = UUID.randomUUID();
        UUID supplyId2 = UUID.randomUUID();
        // DRAFT-style rows are always pending (validFrom == null); these must still be included,
        // otherwise generating a file for a DRAFT agreement would always yield an empty set.
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(supplyId1, new BigDecimal("0.500000"), null, null),
                coefficient(supplyId2, new BigDecimal("0.500000"), null, null));
        when(getCoefficientRepository.findAllBySharingAgreementId(AGREEMENT_ID)).thenReturn(coefficients);
        when(getSupplyRepository.findAllByIds(Set.of(supplyId1, supplyId2)))
                .thenReturn(List.of(supply(supplyId1, CUPS_1), supply(supplyId2, CUPS_2)));

        GeneratedDistributorFile file = service().generate(PLANT_ID, AGREEMENT_ID, 2023);

        assertEquals(REGULATORY_CODE + "_2023.txt", file.getFilename());
    }

    @Test
    void generate_buildsCorrectFilenameAndFormattedSortedLines() {
        stubAgreementAndPlant();
        UUID supplyId1 = UUID.randomUUID();
        UUID supplyId2 = UUID.randomUUID();
        UUID supplyId3 = UUID.randomUUID();
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(supplyId3, new BigDecimal("0.333334"), null, null),
                coefficient(supplyId1, new BigDecimal("0.333333"), null, null),
                coefficient(supplyId2, new BigDecimal("0.333333"), null, null));
        when(getCoefficientRepository.findAllBySharingAgreementId(AGREEMENT_ID)).thenReturn(coefficients);
        when(getSupplyRepository.findAllByIds(Set.of(supplyId1, supplyId2, supplyId3)))
                .thenReturn(List.of(supply(supplyId1, CUPS_1), supply(supplyId2, CUPS_2), supply(supplyId3, CUPS_3)));

        GeneratedDistributorFile file = service().generate(PLANT_ID, AGREEMENT_ID, 2023);

        assertEquals(REGULATORY_CODE + "_2023.txt", file.getFilename());
        String expected = CUPS_1 + ";0,333333\n" + CUPS_2 + ";0,333333\n" + CUPS_3 + ";0,333334\n";
        assertEquals(expected, new String(file.getContent(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
