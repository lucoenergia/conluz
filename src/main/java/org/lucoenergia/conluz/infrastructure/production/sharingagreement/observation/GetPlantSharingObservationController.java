package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/observation",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetPlantSharingObservationController {

    private final GetSharingObservationService service;

    public GetPlantSharingObservationController(GetSharingObservationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Observes how the distributor applies the plant's sharing agreements",
            description = """
                    Compares, day by day, the share of the plant's production each supply of its community
                    received according to Datadis with the coefficients of the plant's PUBLISHED and SUPERSEDED
                    agreements. For each agreement it reports when its coefficients were observed applied, how
                    that compares with the stored dates, the anomalies attributed to it and what to do with it.
                    DRAFT agreements are ignored. Computed on every request from the stored daily data; nothing
                    is written, and no coefficient is ever proposed.

                    When the plant supply has no data, the response says the plant is not computable.

                    **Required: community admin of the plant's community.**

                    Returns 404 if the plant does not exist or if the caller is not a member of its community,
                    to avoid leaking the existence of plants by ID. Returns 403 if the caller is an active member
                    of the plant's community but not a community admin.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getPlantSharingObservation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Observed sharing computed successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canListSharingAgreements(#plantId)")
    public PlantSharingObservationResponse getPlantSharingObservation(@PathVariable UUID plantId) {
        return new PlantSharingObservationResponse(service.observePlant(plantId));
    }
}
