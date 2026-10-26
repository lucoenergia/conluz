package org.lucoenergia.conluz.domain.admin.supply;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportOutcome;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRow;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.ImportSuppliesServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportSuppliesServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock
    private CreateSupplyService createSupplyService;

    private ImportSuppliesService service() {
        return new ImportSuppliesServiceImpl(createSupplyService);
    }

    @Test
    void eachRowIsCreatedOnItsOwn_andAFailingRowDoesNotStopTheNextOnes() {
        SupplyImportRow created = row(" ES0001 ", "12345678A");
        SupplyImportRow alreadyExists = row("ES0002", "12345678A");
        SupplyImportRow userNotFound = row("ES0003", "87654321B");
        SupplyImportRow failed = row("ES0004", "12345678A");
        SupplyImportRow createdLast = row("ES0005", "12345678A");
        when(createSupplyService.create(same(created.supply()), any(), eq(COMMUNITY_ID)))
                .thenReturn(SupplyMother.random().withCode("ES0001").build());
        when(createSupplyService.create(same(alreadyExists.supply()), any(), eq(COMMUNITY_ID)))
                .thenThrow(new SupplyAlreadyExistsException(SupplyCode.of("ES0002")));
        when(createSupplyService.create(same(userNotFound.supply()), any(), eq(COMMUNITY_ID)))
                .thenThrow(new UserNotFoundException());
        when(createSupplyService.create(same(failed.supply()), any(), eq(COMMUNITY_ID)))
                .thenThrow(new IllegalStateException("unexpected"));
        when(createSupplyService.create(same(createdLast.supply()), any(), eq(COMMUNITY_ID)))
                .thenReturn(SupplyMother.random().withCode("ES0005").build());

        List<ImportedSupplyRow> results = service().importSupplies(COMMUNITY_ID,
                List.of(created, alreadyExists, userNotFound, failed, createdLast));

        assertThat(results).containsExactly(
                new ImportedSupplyRow("ES0001", "12345678A", SupplyImportOutcome.CREATED),
                new ImportedSupplyRow("ES0002", "12345678A", SupplyImportOutcome.ALREADY_EXISTS),
                new ImportedSupplyRow("ES0003", "87654321B", SupplyImportOutcome.USER_NOT_FOUND),
                new ImportedSupplyRow("ES0004", "12345678A", SupplyImportOutcome.FAILED),
                new ImportedSupplyRow("ES0005", "12345678A", SupplyImportOutcome.CREATED));
    }

    @Test
    void theOwnerIsLookedUpByThePersonalIdOfTheRow() {
        SupplyImportRow row = row("ES0001", "12345678A");
        when(createSupplyService.create(any(), any(), any())).thenReturn(SupplyMother.random().build());

        service().importSupplies(COMMUNITY_ID, List.of(row));

        verify(createSupplyService).create(same(row.supply()),
                argThat(personalId -> personalId.getPersonalId().equals("12345678A")), eq(COMMUNITY_ID));
    }

    @Test
    void aPersonalIdThatCannotBeReadFailsOnlyItsRow() {
        SupplyImportRow unreadable = row("ES0001", null);

        List<ImportedSupplyRow> results = service().importSupplies(COMMUNITY_ID, List.of(unreadable));

        assertThat(results).containsExactly(new ImportedSupplyRow("ES0001", null, SupplyImportOutcome.FAILED));
    }

    private static SupplyImportRow row(String code, String personalId) {
        return new SupplyImportRow(SupplyMother.random().withCode(code.trim()).build(), code, personalId);
    }
}
