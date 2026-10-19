package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Maps the outcome of an import to its response: each row's outcome to its {@link RestErrorCode}, and each message
 * written wholly from {@link MessageSource} in the caller's language, from the codes alone, so that it never holds a
 * cell value or the validator's text.
 */
@Component
public class ImportMembersResponseMapper {

    private final MessageSource messageSource;

    public ImportMembersResponseMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ImportMembersResponse toResponse(ImportedMembers imported, Locale locale) {
        return new ImportMembersResponse(imported.registered(), imported.failed(),
                imported.rows().stream().map(row -> rowOf(row, locale)).toList());
    }

    private ImportMembersRowResponse rowOf(ImportedMemberRow row, Locale locale) {
        return switch (row.outcome()) {
            case REGISTERED -> new ImportMembersRowResponse(row.line(), ImportMembersRowResponse.Outcome.REGISTERED,
                    null, null, List.of());
            case ALREADY_MEMBER -> failed(row, RestErrorCode.MEMBERSHIP_ALREADY_EXISTS,
                    message(locale, "error.membership.import.row.already.exists"));
            case INVALID -> failed(row, RestErrorCode.MEMBERSHIP_IMPORT_ROW_INVALID,
                    message(locale, "error.membership.import.row.invalid", row.violations().stream()
                            .map(violation -> describe(violation, locale))
                            .collect(Collectors.joining("; "))));
            case INVALID_PERSONAL_ID -> failed(row, RestErrorCode.USER_PERSONAL_ID_INVALID,
                    message(locale, "error.user.personal.id.invalid"));
            case MALFORMED -> failed(row, RestErrorCode.MEMBERSHIP_IMPORT_ROW_MALFORMED,
                    message(locale, "error.membership.import.row.malformed"));
        };
    }

    private static ImportMembersRowResponse failed(ImportedMemberRow row, RestErrorCode code, String message) {
        return new ImportMembersRowResponse(row.line(), ImportMembersRowResponse.Outcome.FAILED, code, message,
                row.violations().stream()
                        .map(violation -> new ImportMembersRowErrorResponse(violation.column(),
                                violation.constraint(), violation.params()))
                        .toList());
    }

    private String describe(ColumnViolation violation, Locale locale) {
        String column = violation.column();
        return switch (violation.constraint()) {
            case REQUIRED -> message(locale, "error.membership.import.row.required", column);
            case NOT_EMPTY_ONCE_NORMALISED ->
                    message(locale, "error.membership.import.row.not.empty.once.normalised", column);
            case INVALID_PERSONAL_ID -> message(locale, "error.membership.import.row.invalid.personal.id", column);
            case MIN -> message(locale, "error.membership.import.row.min", column, violation.params().get("min"));
            case EMAIL -> message(locale, "error.membership.import.row.email", column);
            case NOT_AN_INTEGER -> message(locale, "error.membership.import.row.not.an.integer", column);
        };
    }

    private String message(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
