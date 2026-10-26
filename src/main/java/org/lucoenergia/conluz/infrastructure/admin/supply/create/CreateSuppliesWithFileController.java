package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSuppliesService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportRow;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.PayloadTooLargeErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.io.CsvParseExceptionHandler;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Controller class for importing supplies in bulk from a CSV file.
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateSuppliesWithFileController {

    private final CsvFileRequestValidator csvFileRequestValidator;
    private final CsvFileParser csvFileParser;
    private final ImportSuppliesService importSuppliesService;
    private final ImportSuppliesResponseMapper responseMapper;
    private final CsvParseExceptionHandler csvParseExceptionHandler;

    public CreateSuppliesWithFileController(CsvFileRequestValidator csvFileRequestValidator,
                                            CsvFileParser csvFileParser,
                                            ImportSuppliesService importSuppliesService,
                                            ImportSuppliesResponseMapper responseMapper,
                                            CsvParseExceptionHandler csvParseExceptionHandler) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.importSuppliesService = importSuppliesService;
        this.responseMapper = responseMapper;
        this.csvParseExceptionHandler = csvParseExceptionHandler;
    }

    @PostMapping
    @Operation(
            summary = "Creates supplies in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of supplies within the system by importing a
                    CSV file.
                    
                    This endpoint requires clients to send a request containing a file with essential details for each
                    supply, including code, address, users and any additional relevant information.
                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: Community Admin**
                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with
                    comprehensive details about the result of the bulk operation, including what users have been created
                    or any potential error.
                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "createSuppliesWithFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File processed successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreationInBulkResponse.class))}
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @PayloadTooLargeErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public ResponseEntity createSuppliesWithFile(
            @Parameter(description = "CSV file format: code(String), address(String), addressRef(String), personalId(String; the DNI/NIE/NIF of the existing owner, normalised before the lookup: whitespace, dots and hyphens removed, letters upper-cased). The owner must be a member of the target community: a row whose owner is not a member of it gets the same per-row \"user not found\" error as a row whose personalId matches no user.")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Target community UUID.")
            @RequestParam("communityId") UUID communityId) {

        Optional<ResponseEntity<RestError>> optionalResponseEntity = csvFileRequestValidator.validate(file);
        if (optionalResponseEntity.isPresent()) {
            return optionalResponseEntity.get();
        }
        List<CreateSupplyBody> supplies;
        try {
            supplies = csvFileParser.parse(file.getInputStream(), CreateSupplyBody.class);
        } catch (Exception ex) {
            return csvParseExceptionHandler.handleCsvParsingError(ex);
        }

        List<SupplyImportRow> rows = supplies.stream()
                .map(supply -> new SupplyImportRow(supply.mapToSupply(), supply.getCode(), supply.getPersonalId()))
                .toList();
        List<ImportedSupplyRow> imported = importSuppliesService.importSupplies(communityId, rows);
        CreationInBulkResponse<String, String> response = responseMapper.toResponse(imported,
                LocaleContextHolder.getLocale());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
