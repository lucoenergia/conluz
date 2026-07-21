package org.lucoenergia.conluz.infrastructure.production.plant.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{id}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class UpdateSharingAgreementController {

    private final UpdateSharingAgreementService service;

    public UpdateSharingAgreementController(UpdateSharingAgreementService service) {
        this.service = service;
    }

    @PatchMapping
    @Operation(
            summary = "Updates a DRAFT sharing agreement's name, notes and installed power",
            description = """
                    This endpoint updates the name, notes and installed power of a sharing agreement.
                    Its status, plant and creation metadata can never be changed through this endpoint.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in DRAFT status.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "updateSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The sharing agreement has been successfully updated.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is not in DRAFT status.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Sharing agreement 'ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c' is not in DRAFT status.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": []
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #id)")
    public SharingAgreementResponse updateSharingAgreement(@PathVariable UUID plantId, @PathVariable UUID id,
                                                            @Valid @RequestBody UpdateSharingAgreementBody body) {
        SharingAgreement agreement = service.update(plantId, id, body.getName(), body.getNotes(),
                body.getInstalledPowerKw());
        return new SharingAgreementResponse(agreement);
    }
}
