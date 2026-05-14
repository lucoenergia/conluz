package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementWithPartitionsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.PartitionCoefficientFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(
        value = "/api/v1/sharing-agreements",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateSharingAgreementWithPartitionsController {

    private final CreateSharingAgreementWithPartitionsService service;
    private final SupplyPartitionRepository supplyPartitionRepository;
    private final PartitionCoefficientFileRequestValidator fileRequestValidator;
    private final ErrorBuilder errorBuilder;

    public CreateSharingAgreementWithPartitionsController(
            CreateSharingAgreementWithPartitionsService service,
            SupplyPartitionRepository supplyPartitionRepository,
            PartitionCoefficientFileRequestValidator fileRequestValidator,
            ErrorBuilder errorBuilder) {
        this.service = service;
        this.supplyPartitionRepository = supplyPartitionRepository;
        this.fileRequestValidator = fileRequestValidator;
        this.errorBuilder = errorBuilder;
    }

    @PostMapping
    @Operation(
            summary = "Creates a sharing agreement with supply partitions from a TXT file",
            description = """
                    Creates a new sharing agreement atomically with its supply partitions.
                    The active agreement's end date is automatically set to startDate - 1 day.
                    File format: CUPS;coefficient (semicolon separator, fraction 0-1).
                    **Required Role: ADMIN**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "createSharingAgreementWithPartitions",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement created successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity createSharingAgreementWithPartitions(
            @Parameter(description = "Start date of the agreement (yyyy-MM-dd)", required = true)
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date of the agreement (yyyy-MM-dd)")
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Notes for the agreement")
            @RequestParam(value = "notes", required = false) String notes,
            @Parameter(description = "TXT file with CUPS;coefficient rows", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        Optional<ResponseEntity<RestError>> validationErrors = fileRequestValidator.validate(file);
        if (validationErrors.isPresent()) {
            return validationErrors.get();
        }

        try {
            SharingAgreement agreement = service.create(startDate, endDate, notes, file);
            var partitions = supplyPartitionRepository.findBySharingAgreementId(agreement.getId());
            return ResponseEntity.ok(new SharingAgreementResponse(agreement, partitions));
        } catch (InvalidSupplyPartitionCoefficientException e) {
            return errorBuilder.build(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            return errorBuilder.build(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
