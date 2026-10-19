package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportMembersService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ImportMembersController {

    private final ImportMembersService service;
    private final MessageSource messageSource;

    public ImportMembersController(ImportMembersService service, MessageSource messageSource) {
        this.service = service;
        this.messageSource = messageSource;
    }

    @PostMapping
    @Operation(
            summary = "Imports members into a community from a CSV file.",
            description = """
                    Registers each data row of a CSV file as a member of the community, as `registerMember` does: \
                    a pending membership with the role COMMUNITY_MEMBER and the row as the community's member \
                    record. Only an active community admin of the community may import members; a platform admin \
                    may not.

                    Nothing is sent to the members and no invitation is issued. Invite them afterwards with \
                    `sendInvitations`.

                    **The file.** UTF-8 or Windows-1252, the two encodings Spanish Excel saves a CSV file in \
                    ("CSV UTF-8" and "CSV"), separated by `,` or `;`, told from the header line. A file starting \
                    with a UTF-8 byte order mark is read as UTF-8; one starting with a UTF-16 byte order mark is \
                    refused; any other is read as UTF-8 if it is valid UTF-8, and as Windows-1252 otherwise. The \
                    header line names the columns, in any order: `personalId` and `fullName` are required; \
                    `memberNumber`, `contactEmail`, `address` and `phoneNumber` are optional. No other column is \
                    accepted. Each value follows the rules of the same field of `registerMember`. At most 1000 \
                    data rows; rows whose cells are all empty are skipped.

                    **Whole-file refusals.** If the file is neither UTF-8 nor Windows-1252, its separator cannot \
                    be told, it is not valid CSV, its header names an unknown column, a column twice or lacks a \
                    required one, or it has more than 1000 data rows, the response is 400 with a dedicated code, \
                    and nothing is registered.

                    **Rows.** Otherwise each row is registered in a transaction of its own, so a row that fails \
                    keeps the others. The response has one result per row. A person who already has an account is \
                    registered exactly as a new one, and their result is the same. A person who already has a \
                    membership in this community, including from an earlier row of the same file, fails with \
                    `MEMBERSHIP_ALREADY_EXISTS`. Importing the same file again is therefore safe: rows already \
                    registered fail with that code, and the rest are registered.

                    A row whose values break rules of the registration fails with `MEMBERSHIP_IMPORT_ROW_INVALID` \
                    and lists each rule broken in `errors`, by column and constraint code, for a client to write \
                    its own text. Every `message` is in the language of the request's Accept-Language.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "importMembers",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The file was read, and each row was registered or failed.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The file was refused as a whole and nothing was registered. The code tells why: "
                            + "MEMBERSHIP_IMPORT_INVALID_ENCODING, MEMBERSHIP_IMPORT_AMBIGUOUS_SEPARATOR, "
                            + "MEMBERSHIP_IMPORT_MALFORMED_FILE, MEMBERSHIP_IMPORT_UNKNOWN_HEADERS, "
                            + "MEMBERSHIP_IMPORT_DUPLICATED_HEADERS, MEMBERSHIP_IMPORT_MISSING_HEADERS (each with "
                            + "the offending header names in `params.headers`, comma-separated) or "
                            + "MEMBERSHIP_IMPORT_TOO_MANY_ROWS (with the limit in `params.maxRows`).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-18T10:10:25.534035352+02:00",
                                               "status": 400,
                                               "message": "The file has columns that are not imported: 'password'. The columns are personalId, fullName, memberNumber, contactEmail, address and phoneNumber.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The file has columns that are not imported: 'password'. The columns are personalId, fullName, memberNumber, contactEmail, address and phoneNumber.",
                                                   "code": "MEMBERSHIP_IMPORT_UNKNOWN_HEADERS",
                                                   "params": {
                                                     "headers": "password"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canRegisterMembers(#communityId)")
    public ImportMembersResponse importMembers(
            @AuthenticationPrincipal User currentUser,
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "CSV file of members, UTF-8 or Windows-1252, separated by `,` or `;`, with a "
                    + "header line naming the columns personalId, fullName, memberNumber, contactEmail, address and "
                    + "phoneNumber.",
                    required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportedMembers imported = service.importMembers(communityId, UserId.of(currentUser.getId()),
                file.getBytes());
        return new ImportMembersResponse(imported.registered(), imported.failed(),
                imported.rows().stream().map(this::rowOf).toList());
    }

    private ImportMembersRowResponse rowOf(ImportedMemberRow row) {
        return switch (row.outcome()) {
            case REGISTERED -> new ImportMembersRowResponse(row.line(), ImportMembersRowResponse.Outcome.REGISTERED,
                    null, null, List.of());
            case ALREADY_MEMBER -> failed(row, RestErrorCode.MEMBERSHIP_ALREADY_EXISTS,
                    message("error.membership.import.row.already.exists"));
            case INVALID -> failed(row, RestErrorCode.MEMBERSHIP_IMPORT_ROW_INVALID,
                    message("error.membership.import.row.invalid", row.violations().stream()
                            .map(this::describe)
                            .collect(Collectors.joining("; "))));
            case MALFORMED -> failed(row, RestErrorCode.MEMBERSHIP_IMPORT_ROW_MALFORMED,
                    message("error.membership.import.row.malformed"));
        };
    }

    private static ImportMembersRowResponse failed(ImportedMemberRow row, RestErrorCode code, String message) {
        return new ImportMembersRowResponse(row.line(), ImportMembersRowResponse.Outcome.FAILED, code, message,
                row.violations().stream()
                        .map(violation -> new ImportMembersRowErrorResponse(violation.column(),
                                violation.constraint(), violation.params()))
                        .toList());
    }

    private String describe(ColumnViolation violation) {
        String column = violation.column();
        return switch (violation.constraint()) {
            case REQUIRED -> message("error.membership.import.row.required", column);
            case NOT_EMPTY_ONCE_NORMALISED -> message("error.membership.import.row.not.empty.once.normalised", column);
            case MIN -> message("error.membership.import.row.min", column, violation.params().get("min"));
            case EMAIL -> message("error.membership.import.row.email", column);
            case NOT_AN_INTEGER -> message("error.membership.import.row.not.an.integer", column);
        };
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
