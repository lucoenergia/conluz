package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplies;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow.ColumnViolation;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Maps the outcome of an import of supplies to its response: each row's outcome to its {@link RestErrorCode}, and each
 * message written wholly from {@link MessageSource} in the caller's language, from the codes alone, so that it never
 * holds a cell value.
 */
@Component
public class ImportSuppliesResponseMapper {

    private final MessageSource messageSource;

    public ImportSuppliesResponseMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ImportSuppliesResponse toResponse(ImportedSupplies imported, Locale locale) {
        return new ImportSuppliesResponse(imported.created(), imported.failed(),
                imported.rows().stream().map(row -> rowOf(row, locale)).toList());
    }

    private ImportSuppliesRowResponse rowOf(ImportedSupplyRow row, Locale locale) {
        return switch (row.outcome()) {
            case CREATED -> new ImportSuppliesRowResponse(row.line(), ImportSuppliesRowResponse.Outcome.CREATED,
                    null, null, List.of());
            case INVALID -> failed(row, RestErrorCode.SUPPLY_IMPORT_ROW_INVALID,
                    message(locale, "error.supply.import.row.invalid", row.violations().stream()
                            .map(violation -> describe(violation, locale))
                            .collect(Collectors.joining("; "))));
            case MALFORMED -> failed(row, RestErrorCode.SUPPLY_IMPORT_ROW_MALFORMED,
                    message(locale, "error.supply.import.row.malformed"));
            case OWNER_NOT_FOUND -> failed(row, RestErrorCode.SUPPLY_OWNER_NOT_FOUND,
                    message(locale, "error.supply.owner.not.found"));
            case ALREADY_EXISTS -> failed(row, RestErrorCode.SUPPLY_ALREADY_EXISTS,
                    message(locale, "error.supply.import.row.already.exists"));
            case REGISTERED_IN_ANOTHER_COMMUNITY -> failed(row, RestErrorCode.SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY,
                    message(locale, "error.supply.registered.in.another.community"));
        };
    }

    private static ImportSuppliesRowResponse failed(ImportedSupplyRow row, RestErrorCode code, String message) {
        return new ImportSuppliesRowResponse(row.line(), ImportSuppliesRowResponse.Outcome.FAILED, code, message,
                row.violations().stream()
                        .map(violation -> new ImportSuppliesRowErrorResponse(violation.column(),
                                violation.constraint(), violation.params()))
                        .toList());
    }

    private String describe(ColumnViolation violation, Locale locale) {
        String column = violation.column();
        return switch (violation.constraint()) {
            case REQUIRED -> message(locale, "error.supply.import.row.required", column);
            case INVALID_CUPS -> message(locale, "error.supply.import.row.invalid.cups", column);
        };
    }

    private String message(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
