package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
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
            description = "This endpoint updates a sharing agreement with the specified ID.",
            tags = ApiTag.SUPPLIES,
            operationId = "updateSharingAgreement"
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
    public SharingAgreementResponse updateSharingAgreement(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateSharingAgreementBody body) {
        SharingAgreement sharingAgreement = service.update(id, body.getStartDate(), body.getEndDate());
        return new SharingAgreementResponse(sharingAgreement);
    }
}