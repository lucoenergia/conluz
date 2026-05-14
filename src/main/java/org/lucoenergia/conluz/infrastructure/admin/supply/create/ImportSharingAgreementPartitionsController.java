package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSharingAgreementPartitionsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.ImportSharingAgreementPartitionsResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.PartitionCoefficientFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/sharing-agreements",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ImportSharingAgreementPartitionsController {

    private final ImportSharingAgreementPartitionsService service;
    private final PartitionCoefficientFileRequestValidator fileRequestValidator;
    private final ErrorBuilder errorBuilder;

    public ImportSharingAgreementPartitionsController(
            ImportSharingAgreementPartitionsService service,
            PartitionCoefficientFileRequestValidator fileRequestValidator,
            ErrorBuilder errorBuilder) {
        this.service = service;
        this.fileRequestValidator = fileRequestValidator;
        this.errorBuilder = errorBuilder;
    }

    @PostMapping("/{id}/supply-partitions/import")
    @Operation(
            summary = "Imports supply partitions for a sharing agreement from a TXT file",
            description = """
                    Replaces or creates supply partitions on an existing agreement.
                    File format: CUPS;coefficient (semicolon separator, fraction 0-1).
                    **Required Role: ADMIN**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "importSharingAgreementPartitions",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Partitions imported successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity importPartitions(
            @PathVariable("id") UUID id,
            @Parameter(description = "TXT file with CUPS;coefficient rows", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        Optional<ResponseEntity<RestError>> validationErrors = fileRequestValidator.validate(file);
        if (validationErrors.isPresent()) {
            return validationErrors.get();
        }

        try {
            ImportSharingAgreementPartitionsResponse response =
                    service.importPartitions(SharingAgreementId.of(id), file);
            return ResponseEntity.ok(response);
        } catch (InvalidSupplyPartitionCoefficientException e) {
            return errorBuilder.build(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            return errorBuilder.build(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
