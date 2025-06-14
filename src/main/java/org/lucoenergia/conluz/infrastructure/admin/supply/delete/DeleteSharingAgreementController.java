package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for deleting sharing agreements
 */
@RestController
@RequestMapping("/api/v1/sharing-agreements")
public class DeleteSharingAgreementController {

    private final DeleteSharingAgreementService service;

    public DeleteSharingAgreementController(DeleteSharingAgreementService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Removes a sharing agreement by ID",
            description = """
                    This endpoint enables the removal of a sharing agreement from the system by specifying the agreement's
                    unique identifier within the endpoint path.

                    To utilize this endpoint, clients send a DELETE request with the targeted agreement's ID, requiring
                    authentication for secure access.
                    **Required Role: ADMIN**

                    Upon successful deletion, the server responds with an HTTP status code of 200, indicating that the
                    sharing agreement has been successfully removed.

                    In cases where the deletion process encounters errors, the server returns an appropriate error
                    status code, along with a descriptive error message to guide clients in diagnosing and addressing
                    the issue.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "deleteSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement deleted successfully"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteSharingAgreement(@PathVariable("id") UUID id) {
        service.delete(SharingAgreementId.of(id));
    }
}