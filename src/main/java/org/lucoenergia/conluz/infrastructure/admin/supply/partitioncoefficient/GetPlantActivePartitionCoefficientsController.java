package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/partition-coefficients/active",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetPlantActivePartitionCoefficientsController {

    private final PartitionCoefficientService service;

    public GetPlantActivePartitionCoefficientsController(PartitionCoefficientService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the partition coefficients currently in force in a plant",
            description = """
                    Returns every partition coefficient currently in force in this plant, one per
                    supply. A coefficient is in force when it has been activated (validFrom is set)
                    and has not been closed (validTo is null); pending coefficients, including every
                    coefficient of a DRAFT agreement, and closed coefficients are excluded.

                    Coefficients are returned whatever the status of the agreement that authored
                    them, and different supplies may be on coefficients authored by different
                    agreements -- while the distributor is applying a plant's newest agreement, some
                    supplies have already moved to it and the rest are still on the previous one.
                    The coefficients therefore do not necessarily sum to 1: values are reported
                    exactly as stored, without normalisation.

                    Only coefficients of this plant are returned; a supply that participates in more
                    than one plant appears here only for this one. Ordered by CUPS ascending. A plant
                    with nothing in force returns an empty list.

                    **Required: community admin of the plant's community.**

                    Returns 404 if the plant does not exist or if the caller is not a member of its
                    community, to avoid leaking the existence of plants by ID.
                    Returns 403 if the caller is an enabled member of the plant's community but
                    not a community admin.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getPlantActivePartitionCoefficients",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Coefficients in force retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId)")
    public List<PartitionCoefficientResponse> getActiveByPlant(@PathVariable UUID plantId) {
        return service.findActiveByPlantId(plantId).stream()
                .map(PartitionCoefficientResponse::new)
                .toList();
    }
}
