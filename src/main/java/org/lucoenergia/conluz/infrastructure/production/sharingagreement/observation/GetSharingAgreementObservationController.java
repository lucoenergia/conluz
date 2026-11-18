package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.GetSharingObservationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/observation",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementObservationController {

    private final GetSharingObservationService service;

    public GetSharingAgreementObservationController(GetSharingObservationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Observes how the distributor applies one sharing agreement",
            description = """
                    One agreement's part of the plant's observed sharing (getPlantSharingObservation): the same
                    summary, with the observation of each of its coefficient rows and its leaving rows, the
                    supplies absent from it that had a non-zero value in the agreement observed before. Computed
                    on every request from the stored daily data; nothing is written.

                    **Required: community admin of the plant's community.**

                    Returns 404 if the plant does not exist, if the caller is not a member of its community, or if
                    the sharing agreement does not exist or does not belong to this plant, to avoid leaking the
                    existence of plants or agreements by ID. Returns 403 if the caller is an active member of the
                    plant's community but not a community admin. Returns 409 with code
                    SHARING_AGREEMENT_NOT_OBSERVABLE if the agreement is a DRAFT, which is not in force.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getSharingAgreementObservation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Observed sharing of the agreement computed successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is a DRAFT, so it is not observed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Sharing agreement 'ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c' is DRAFT; only published and superseded agreements are observed.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "code": "SHARING_AGREEMENT_NOT_OBSERVABLE",
                                                   "message": "Sharing agreement 'ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c' is DRAFT; only published and superseded agreements are observed."
                                                 }
                                               ]
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
    @PreAuthorize("@communityAccessGuard.canReadSharingAgreement(#plantId, #sharingAgreementId)")
    public SharingAgreementObservationResponse getSharingAgreementObservation(@PathVariable UUID plantId,
                                                                              @PathVariable UUID sharingAgreementId) {
        return new SharingAgreementObservationResponse(service.observeAgreement(plantId, sharingAgreementId));
    }
}
