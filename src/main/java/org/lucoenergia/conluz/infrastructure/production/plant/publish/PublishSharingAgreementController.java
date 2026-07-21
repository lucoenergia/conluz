package org.lucoenergia.conluz.infrastructure.production.plant.publish;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.publish.PublishSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/{id}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublishSharingAgreementController {

    private final PublishSharingAgreementService service;

    public PublishSharingAgreementController(PublishSharingAgreementService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Publishes a DRAFT sharing agreement",
            description = """
                    This endpoint transitions a sharing agreement from DRAFT to PUBLISHED. The
                    agreement must already have at least one partition coefficient.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in DRAFT status, or if it has no partition
                    coefficients yet.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "publishSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The sharing agreement has been successfully published.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is not in DRAFT status, or it has no partition coefficients yet.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Sharing agreement 'ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c' cannot be published because it has no partition coefficients.",
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
    public SharingAgreementResponse publishSharingAgreement(@PathVariable UUID plantId, @PathVariable UUID id) {
        SharingAgreement agreement = service.publish(plantId, id);
        return new SharingAgreementResponse(agreement);
    }
}
