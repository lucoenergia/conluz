package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.delete.DeleteSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeleteSharingAgreementController {

    private final DeleteSharingAgreementService service;

    public DeleteSharingAgreementController(DeleteSharingAgreementService service) {
        this.service = service;
    }

    @DeleteMapping
    @Operation(
            summary = "Removes a DRAFT sharing agreement",
            description = """
                    This endpoint removes a sharing agreement. Only DRAFT agreements can be deleted:
                    deleting a PUBLISHED agreement would destroy the historical basis of past billing.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in DRAFT status.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "deleteSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement deleted successfully"
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
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #id)")
    public void deleteSharingAgreement(@PathVariable UUID plantId, @PathVariable UUID id) {
        service.delete(plantId, id);
    }
}
