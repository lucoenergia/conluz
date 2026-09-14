package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.revert.RevertSharingAgreementToDraftService;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementResponse;
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
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/revert-to-draft", produces = MediaType.APPLICATION_JSON_VALUE)
public class RevertSharingAgreementToDraftController {

    private final RevertSharingAgreementToDraftService service;

    public RevertSharingAgreementToDraftController(RevertSharingAgreementToDraftService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Reverts a PUBLISHED sharing agreement back to DRAFT",
            description = """
                    This endpoint transitions a sharing agreement from PUBLISHED back to DRAFT, making
                    its coefficient set editable again. Only allowed while the agreement is inert: none
                    of its coefficients may have been applied by the distributor yet (none may have
                    validFrom set).

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in PUBLISHED status (already DRAFT, or
                    SUPERSEDED), or if any of its coefficients has already been applied by the
                    distributor.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "revertSharingAgreementToDraft",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The sharing agreement has been successfully reverted to DRAFT.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is not in PUBLISHED status, or it has at least one applied coefficient.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Sharing agreement 'ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c' has at least one applied coefficient and cannot be reverted to DRAFT.",
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
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public SharingAgreementResponse revertSharingAgreementToDraft(@PathVariable UUID plantId, @PathVariable UUID sharingAgreementId) {
        SharingAgreement agreement = service.revertToDraft(plantId, sharingAgreementId);
        return new SharingAgreementResponse(agreement);
    }
}
