package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantResponse;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Get all plants of a community
 */
@RestController
@RequestMapping(value = "/api/v1/communities/{communityId}/plants")
public class GetAllPlantsController {

    private final GetPlantService service;
    private final PaginationRequestMapper paginationRequestMapper;

    public GetAllPlantsController(GetPlantService service, PaginationRequestMapper paginationRequestMapper) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the plants of a community with support for pagination, filtering, and sorting.",
            description = """
                    Retrieves the plants of the given community with pagination, filtering and sorting. Requires
                    authentication through a Bearer Token.

                    **Required: any member of the community (any role).**""",
            tags = ApiTag.PLANTS,
            operationId = "getAllPlants"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PageableAsQueryParam
    @PreAuthorize("@communityAccessGuard.canListPlants(#communityId)")
    public PagedResult<PlantResponse> getAllPlants(@PathVariable("communityId") UUID communityId,
                                                   @Parameter(hidden = true) Pageable page) {
        PagedResult<Plant> plants = service.findAllByCommunities(paginationRequestMapper.mapRequest(page),
                Set.of(communityId));

        List<PlantResponse> plantsResponse = plants.getItems().stream()
                .map(PlantResponse::new)
                .toList();

        return new PagedResult<>(plantsResponse, plants.getSize(), plants.getTotalElements(),
                plants.getTotalPages(), plants.getNumber());
    }
}
