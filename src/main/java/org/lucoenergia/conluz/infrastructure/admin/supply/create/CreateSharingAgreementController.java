package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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
            summary = """
                    This endpoint facilitates the creation of a new sharing agreement within the system.
                    
                    To utilize this endpoint, clients send a request containing essential details such as the agreement's
                    start and end dates.
                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required Role: ADMIN**
                    
                    Upon successful creation, the server responds with an HTTP status code of 200, providing comprehensive
                    details about the newly created sharing agreement.
                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error
                    status code, accompanied by a descriptive error message to guide clients in addressing and resolving
                    the issue.
                    """,
            description = "This endpoint creates a new sharing agreement with the specified start and end dates.",
            tags = ApiTag.SUPPLIES,
            operationId = "createSharingAgreement",
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
    public SharingAgreementResponse createSharingAgreement(@Valid @RequestBody CreateSharingAgreementBody body) {
        SharingAgreement sharingAgreement = service.create(body.getStartDate(), body.getEndDate());
        return new SharingAgreementResponse(sharingAgreement);
    }
}