package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
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
            summary = "Deletes a sharing agreement",
            description = "This endpoint deletes a sharing agreement with the specified ID.",
            tags = ApiTag.SUPPLIES,
            operationId = "deleteSharingAgreement"
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
    public void deleteSharingAgreement(@PathVariable("id") UUID id) {
        service.delete(SharingAgreementId.of(id));
    }
}