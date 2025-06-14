package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for updating sharing agreements
 */
@RestController
@RequestMapping(
        value = "/api/v1/sharing-agreements",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UpdateSharingAgreementController {

    private final UpdateSharingAgreementService service;

    public UpdateSharingAgreementController(UpdateSharingAgreementService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Updates a sharing agreement",
            description = """
                    This endpoint enables the update of sharing agreement information by specifying the agreement's unique
                    identifier in the endpoint path.
    
                    Clients send a request containing the updated agreement details, and authentication, through an
                    authentication token, is required for secure access.
                    **Required Role: ADMIN**
    
                    A successful update results in an HTTP status code of 200, indicating that the sharing agreement
                    information has been successfully modified and returning the updated agreement details.
    
                    In cases where the update encounters errors, the server responds with an appropriate error status
                    code, along with a descriptive error message to guide clients in addressing and resolving the issue.
    
                    If you don't provide some of the optional parameters, they will be considered as null value so their
                    values will be updated with a null value.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "updateSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement updated successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public SharingAgreementResponse updateSharingAgreement(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateSharingAgreementBody body) {
        SharingAgreement sharingAgreement = service.update(id, body.getStartDate(), body.getEndDate());
        return new SharingAgreementResponse(sharingAgreement);
    }
}