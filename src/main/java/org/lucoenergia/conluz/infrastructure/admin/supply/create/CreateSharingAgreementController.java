package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for creating sharing agreements
 */
@RestController
@RequestMapping(
        value = "/api/v1/sharing-agreements",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateSharingAgreementController {

    private final CreateSharingAgreementService service;

    public CreateSharingAgreementController(CreateSharingAgreementService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new sharing agreement",
            description = "This endpoint creates a new sharing agreement with the specified start and end dates.",
            tags = ApiTag.SUPPLIES,
            operationId = "createSharingAgreement"
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
    public SharingAgreementResponse createSharingAgreement(@Valid @RequestBody CreateSharingAgreementBody body) {
        SharingAgreement sharingAgreement = service.create(body.getStartDate(), body.getEndDate());
        return new SharingAgreementResponse(sharingAgreement);
    }
}