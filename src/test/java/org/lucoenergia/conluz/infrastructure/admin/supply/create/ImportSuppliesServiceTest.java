package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyRegisteredInAnotherCommunityException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplies;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRowOutcome;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileProblem;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileReader;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileReaderImpl;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileRefusedException;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportSuppliesServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final String CUPS = "ES0031300325733001FH0F";

    private final CreateSupplyService createSupplyService = mock(CreateSupplyService.class);
    private final ImportSuppliesService service = new ImportSuppliesServiceImpl(new CsvImportFileReaderImpl(),
            createSupplyService);

    @Test
    @DisplayName("SUP-005 each valid row is created as createSupply creates it, with the cells as they are and the "
            + "empty optional ones as null")
    void aValidRow_isCreatedWithItsCells() {
        ImportedSupplies imported = service.importSupplies(COMMUNITY_ID, csv("code;address;personalId;addressRef;name\n"
                + "es0031300325733001fh0f; Calle Mayor 1 ;12345678Z;;\n"
                + CUPS.replace("01FH", "02FH") + ";Calle Mayor 2;87654321X;Bajo;Casa\n"));

        assertThat(imported.rows()).extracting(ImportedSupplyRow::line, ImportedSupplyRow::outcome)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2, SupplyImportRowOutcome.CREATED),
                        org.assertj.core.groups.Tuple.tuple(3, SupplyImportRowOutcome.CREATED));
        ArgumentCaptor<Supply> supplies = ArgumentCaptor.forClass(Supply.class);
        ArgumentCaptor<UserPersonalId> owners = ArgumentCaptor.forClass(UserPersonalId.class);
        verify(createSupplyService, org.mockito.Mockito.times(2)).create(supplies.capture(), owners.capture(),
                eq(COMMUNITY_ID));
        Supply first = supplies.getAllValues().get(0);
        assertThat(first.getCode()).isEqualTo("es0031300325733001fh0f");
        assertThat(first.getAddress()).isEqualTo(" Calle Mayor 1 ");
        assertThat(first.getAddressRef()).isNull();
        assertThat(first.getName()).isNull();
        Supply second = supplies.getAllValues().get(1);
        assertThat(second.getAddressRef()).isEqualTo("Bajo");
        assertThat(second.getName()).isEqualTo("Casa");
        assertThat(owners.getAllValues()).extracting(UserPersonalId::getPersonalId)
                .containsExactly("12345678Z", "87654321X");
        assertThat(imported.created()).isEqualTo(2);
        assertThat(imported.failed()).isZero();
    }

    @Test
    @DisplayName("SUP-005 each refusal of createSupply is the row's outcome, and the next rows are still created")
    void eachRefusal_isARowOutcome() {
        when(createSupplyService.create(argThat(supply -> supply != null && supply.getAddress().equals("A")), any(),
                any())).thenThrow(new SupplyOwnerNotFoundException());
        when(createSupplyService.create(argThat(supply -> supply != null && supply.getAddress().equals("B")), any(),
                any())).thenThrow(new SupplyAlreadyExistsException());
        when(createSupplyService.create(argThat(supply -> supply != null && supply.getAddress().equals("C")), any(),
                any())).thenThrow(new SupplyRegisteredInAnotherCommunityException());

        ImportedSupplies imported = service.importSupplies(COMMUNITY_ID, csv("code;address;personalId\n"
                + CUPS + ";A;1\n" + CUPS + ";B;1\n" + CUPS + ";C;1\n" + CUPS + ";D;1\n"));

        assertThat(imported.rows()).extracting(ImportedSupplyRow::outcome).containsExactly(
                SupplyImportRowOutcome.OWNER_NOT_FOUND, SupplyImportRowOutcome.ALREADY_EXISTS,
                SupplyImportRowOutcome.REGISTERED_IN_ANOTHER_COMMUNITY, SupplyImportRowOutcome.CREATED);
        assertThat(imported.created()).isEqualTo(1);
        assertThat(imported.failed()).isEqualTo(3);
    }

    @Test
    @DisplayName("SUP-006 a row whose code is not a CUPS, or with an empty required cell, lists every rule broken in "
            + "the order of the columns and is not created")
    void anInvalidRow_listsEveryRuleBroken_andIsNotCreated() {
        ImportedSupplies imported = service.importSupplies(COMMUNITY_ID, csv("personalId;address;code\n"
                + " ; ;ES1\n" + "1;Calle;\n"));

        assertThat(imported.rows().get(0).outcome()).isEqualTo(SupplyImportRowOutcome.INVALID);
        assertThat(imported.rows().get(0).violations()).extracting(v -> v.column() + ":" + v.constraint())
                .containsExactly("code:INVALID_CUPS", "address:REQUIRED", "personalId:REQUIRED");
        assertThat(imported.rows().get(1).violations()).extracting(v -> v.column() + ":" + v.constraint())
                .containsExactly("code:REQUIRED");
        verifyNoInteractions(createSupplyService);
    }

    @Test
    @DisplayName("SUP-005 a malformed row is not created")
    void aMalformedRow_isNotCreated() {
        ImportedSupplies imported = service.importSupplies(COMMUNITY_ID, csv("code;address;personalId\n"
                + CUPS + ";Calle\n"));

        assertThat(imported.rows()).extracting(ImportedSupplyRow::outcome)
                .containsExactly(SupplyImportRowOutcome.MALFORMED);
        verifyNoInteractions(createSupplyService);
    }

    @ParameterizedTest
    @EnumSource(CsvImportFileProblem.class)
    @DisplayName("SUP-005 each refusal of the file is the supply import's own, with the same headers, and nothing is "
            + "created")
    void eachFileRefusal_isMappedToTheSupplyImport(CsvImportFileProblem problem) {
        CsvImportFileReader reader = mock(CsvImportFileReader.class);
        when(reader.read(any(), any())).thenThrow(new CsvImportFileRefusedException(problem, List.of("a", "b")));
        ImportSuppliesService refusing = new ImportSuppliesServiceImpl(reader, createSupplyService);

        assertThatThrownBy(() -> refusing.importSupplies(COMMUNITY_ID, new byte[0]))
                .isInstanceOfSatisfying(SupplyImportFileRefusedException.class, e -> {
                    assertThat(e.getProblem().name()).isEqualTo(problem.name());
                    assertThat(e.getHeaders()).containsExactly("a", "b");
                });
        verifyNoInteractions(createSupplyService);
    }

    @Test
    @DisplayName("SUP-005 the file accepts exactly the columns code, address, personalId, addressRef and name, of "
            + "which the first three are required, and at most 1000 rows")
    void theLayout_isTheSupplyColumns() {
        assertThatThrownBy(() -> service.importSupplies(COMMUNITY_ID, csv("code;address;personalId;partition\n")))
                .isInstanceOfSatisfying(SupplyImportFileRefusedException.class,
                        e -> assertThat(e.getHeaders()).containsExactly("partition"));
        assertThatThrownBy(() -> service.importSupplies(COMMUNITY_ID, csv("addressRef;name\n")))
                .isInstanceOfSatisfying(SupplyImportFileRefusedException.class,
                        e -> assertThat(e.getHeaders()).containsExactly("code", "address", "personalId"));
        StringBuilder rows = new StringBuilder("code;address;personalId\n");
        for (int i = 0; i < ImportSuppliesService.MAX_ROWS; i++) {
            rows.append(CUPS).append(";Calle;1\n");
        }
        assertThat(service.importSupplies(COMMUNITY_ID, csv(rows.toString())).rows()).hasSize(1000);
        assertThatThrownBy(() -> service.importSupplies(COMMUNITY_ID, csv(rows + CUPS + ";Calle;1\n")))
                .isInstanceOf(SupplyImportFileRefusedException.class);
    }

    private static byte[] csv(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
