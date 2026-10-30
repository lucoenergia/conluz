package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplies;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportConstraint;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRowOutcome;
import org.lucoenergia.conluz.infrastructure.shared.i18n.I18nConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ImportSuppliesResponseMapperTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final ImportSuppliesResponseMapper mapper =
            new ImportSuppliesResponseMapper(new I18nConfiguration().messageSource());

    @Test
    @DisplayName("SUP-005 the response carries the counts and one result per row, in order, with each outcome's code")
    void eachOutcome_isMappedToItsCode() {
        ImportSuppliesResponse response = mapper.toResponse(new ImportedSupplies(List.of(
                ImportedSupplyRow.of(2, SupplyImportRowOutcome.CREATED),
                invalid(3, ColumnViolation.of("code", SupplyImportConstraint.INVALID_CUPS)),
                ImportedSupplyRow.of(4, SupplyImportRowOutcome.MALFORMED),
                ImportedSupplyRow.of(5, SupplyImportRowOutcome.OWNER_NOT_FOUND),
                ImportedSupplyRow.of(6, SupplyImportRowOutcome.ALREADY_EXISTS),
                ImportedSupplyRow.of(7, SupplyImportRowOutcome.REGISTERED_IN_ANOTHER_COMMUNITY),
                ImportedSupplyRow.of(8, SupplyImportRowOutcome.CREATED))), Locale.ENGLISH);

        assertThat(response.getCreated()).isEqualTo(2);
        assertThat(response.getFailed()).isEqualTo(5);
        assertThat(response.getRows())
                .extracting(ImportSuppliesRowResponse::getLine, ImportSuppliesRowResponse::getOutcome,
                        ImportSuppliesRowResponse::getCode)
                .containsExactly(
                        tuple(2, ImportSuppliesRowResponse.Outcome.CREATED, null),
                        tuple(3, ImportSuppliesRowResponse.Outcome.FAILED, RestErrorCode.SUPPLY_IMPORT_ROW_INVALID),
                        tuple(4, ImportSuppliesRowResponse.Outcome.FAILED, RestErrorCode.SUPPLY_IMPORT_ROW_MALFORMED),
                        tuple(5, ImportSuppliesRowResponse.Outcome.FAILED, RestErrorCode.SUPPLY_OWNER_NOT_FOUND),
                        tuple(6, ImportSuppliesRowResponse.Outcome.FAILED, RestErrorCode.SUPPLY_ALREADY_EXISTS),
                        tuple(7, ImportSuppliesRowResponse.Outcome.FAILED,
                                RestErrorCode.SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY),
                        tuple(8, ImportSuppliesRowResponse.Outcome.CREATED, null));
        assertThat(response.getRows().get(0).getMessage()).isNull();
        assertThat(response.getRows()).filteredOn(row -> row.getLine() != 3)
                .allSatisfy(row -> assertThat(row.getErrors()).isEmpty());
        assertThat(response.getRows()).filteredOn(row -> row.getOutcome() == ImportSuppliesRowResponse.Outcome.FAILED)
                .allSatisfy(row -> assertThat(row.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("SUP-005 an invalid row's errors carry each violation's column, constraint and parameters, and its "
            + "message names each rule")
    void anInvalidRow_carriesItsErrors() {
        ImportSuppliesRowResponse row = mapper.toResponse(new ImportedSupplies(List.of(invalid(2,
                ColumnViolation.of("code", SupplyImportConstraint.INVALID_CUPS),
                ColumnViolation.of("address", SupplyImportConstraint.REQUIRED)))), Locale.ENGLISH).getRows().get(0);

        assertThat(row.getErrors())
                .extracting(ImportSuppliesRowErrorResponse::getColumn, ImportSuppliesRowErrorResponse::getConstraint,
                        ImportSuppliesRowErrorResponse::getParams)
                .containsExactly(tuple("code", SupplyImportConstraint.INVALID_CUPS, Map.of()),
                        tuple("address", SupplyImportConstraint.REQUIRED, Map.of()));
        assertThat(row.getMessage()).isEqualTo("The row has invalid values: code is not a CUPS: it must have 20 or 22 "
                + "characters, without spaces; address is required.");
    }

    @Test
    @DisplayName("SUP-005 the messages follow the request's language")
    void messages_followTheLanguage() {
        List<ImportSuppliesRowResponse> rows = mapper.toResponse(new ImportedSupplies(List.of(
                invalid(2, ColumnViolation.of("code", SupplyImportConstraint.INVALID_CUPS)),
                ImportedSupplyRow.of(3, SupplyImportRowOutcome.ALREADY_EXISTS),
                ImportedSupplyRow.of(4, SupplyImportRowOutcome.REGISTERED_IN_ANOTHER_COMMUNITY))), SPANISH)
                .getRows();

        assertThat(rows).extracting(ImportSuppliesRowResponse::getMessage).containsExactly(
                "La fila tiene valores no válidos: code no es un CUPS: debe tener 20 o 22 caracteres, sin espacios.",
                "Ya hay un suministro de esta comunidad con este CUPS, quizá de una fila anterior del fichero, "
                        + "escrito con 20 o 22 caracteres o con otras mayúsculas y minúsculas.",
                "Este CUPS ya está registrado en otra comunidad, así que no se puede registrar en esta.");
    }

    private static ImportedSupplyRow invalid(int line, ColumnViolation... violations) {
        return new ImportedSupplyRow(line, SupplyImportRowOutcome.INVALID, List.of(violations));
    }
}
