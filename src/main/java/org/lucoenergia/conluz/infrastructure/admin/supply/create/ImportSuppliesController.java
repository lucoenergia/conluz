package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplies;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.PayloadTooLargeErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/supplies/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ImportSuppliesController {

    private final ImportSuppliesService service;
    private final ImportSuppliesResponseMapper responseMapper;

    public ImportSuppliesController(ImportSuppliesService service, ImportSuppliesResponseMapper responseMapper) {
        this.service = service;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    @Operation(
            summary = "Imports supplies into a community from a CSV file.",
            description = """
                    Creates a supply in the community for each data row of a CSV file, as `createSupply` does. Only \
                    an active community admin of the community may import supplies; a platform admin may not.

                    **The file.** UTF-8 or Windows-1252, the two encodings Spanish Excel saves a CSV file in \
                    ("CSV UTF-8" and "CSV"), separated by `,` or `;`, told from the header line. A file starting \
                    with a UTF-8 byte order mark is read as UTF-8; one starting with a UTF-16 byte order mark is \
                    refused; any other is read as UTF-8 if it is valid UTF-8, and as Windows-1252 otherwise. The \
                    header line names the columns, in any order: `code`, `address` and `personalId` are required; \
                    `addressRef` and `name` are optional. No other column is accepted. At most 1000 data rows; rows \
                    whose cells are all empty are skipped.

                    **Whole-file refusals.** If the file is neither UTF-8 nor Windows-1252, its separator cannot \
                    be told, it is not valid CSV, its header names an unknown column, a column twice or lacks a \
                    required one, or it has more than 1000 data rows, the response is 400 with a dedicated code, \
                    and nothing is created.

                    **Rows.** Otherwise each row is created in a transaction of its own, so a row that fails keeps \
                    the others. The response has one result per row. An empty `addressRef` or `name` is stored as \
                    null. A row with an empty required value, or whose `code` is not a CUPS (once upper-cased, it \
                    holds whitespace or is neither 20 nor 22 characters long), fails with \
                    `SUPPLY_IMPORT_ROW_INVALID` and lists each rule broken in `errors`, by column and constraint \
                    code, for a client to write its own text. A row whose `personalId` matches no member of this \
                    community fails with `SUPPLY_OWNER_NOT_FOUND`, whether or not it matches a user; a member whose \
                    membership is pending is an owner. The code is stored upper-cased, and is the same CUPS as \
                    another once both are completed to 22 characters (a 20-character one with `0F`) and compared \
                    case-insensitively. A CUPS a supply of this community already has, including from an earlier \
                    row of the same file, fails with `SUPPLY_ALREADY_EXISTS`, so importing the same file again is \
                    safe: rows already created fail with that code, and the rest are created. A CUPS only a supply \
                    of another community has fails with `SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY`. No result \
                    carries a value of the file. Every `message` is in the language of the request's \
                    Accept-Language.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "importSupplies",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The file was read, and each row was created or failed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ImportSuppliesResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "created": 1,
                                               "failed": 1,
                                               "rows": [
                                                 {
                                                   "line": 2,
                                                   "outcome": "CREATED",
                                                   "code": null,
                                                   "message": null,
                                                   "errors": []
                                                 },
                                                 {
                                                   "line": 3,
                                                   "outcome": "FAILED",
                                                   "code": "SUPPLY_IMPORT_ROW_INVALID",
                                                   "message": "The row has invalid values: code is not a CUPS: it must have 20 or 22 characters, without spaces.",
                                                   "errors": [
                                                     {
                                                       "column": "code",
                                                       "constraint": "INVALID_CUPS",
                                                       "params": {}
                                                     }
                                                   ]
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The file was refused as a whole and nothing was created. The code tells why: "
                            + "SUPPLY_IMPORT_INVALID_ENCODING, SUPPLY_IMPORT_AMBIGUOUS_SEPARATOR, "
                            + "SUPPLY_IMPORT_MALFORMED_FILE, SUPPLY_IMPORT_UNKNOWN_HEADERS, "
                            + "SUPPLY_IMPORT_DUPLICATED_HEADERS, SUPPLY_IMPORT_MISSING_HEADERS (each with "
                            + "the offending header names in `params.headers`, comma-separated) or "
                            + "SUPPLY_IMPORT_TOO_MANY_ROWS (with the limit in `params.maxRows`).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-29T10:10:25.534035352+01:00",
                                               "status": 400,
                                               "message": "The file lacks these required columns: 'personalId'. Nothing has been imported.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The file lacks these required columns: 'personalId'. Nothing has been imported.",
                                                   "code": "SUPPLY_IMPORT_MISSING_HEADERS",
                                                   "params": {
                                                     "headers": "personalId"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @PayloadTooLargeErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public ImportSuppliesResponse importSupplies(
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "CSV file of supplies, UTF-8 or Windows-1252, separated by `,` or `;`, with a "
                    + "header line naming the columns code, address, personalId, addressRef and name.",
                    required = true)
            @RequestParam("file") MultipartFile file,
            Locale locale) throws IOException {
        ImportedSupplies imported = service.importSupplies(communityId, file.getBytes());
        return responseMapper.toResponse(imported, locale);
    }
}
