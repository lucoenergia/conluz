package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Get all plants registered in the energy community
 */
@RestController
@RequestMapping(value = "/api/v1/plants")
public class GetAllPlantsController {

    private final GetPlantService service;
    private final PaginationRequestMapper paginationRequestMapper;
    private final CommunityAccessGuard communityAccessGuard;

    public GetAllPlantsController(GetPlantService service, PaginationRequestMapper paginationRequestMapper,
                                  CommunityAccessGuard communityAccessGuard) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the plants visible to the current user with support for pagination, filtering, and sorting.",
            description = """
                    Retrieves plants with pagination, filtering and sorting. Requires authentication through a Bearer Token.

                    **Visibility:** Platform admins see all plants. Any member of a community sees all plants of the
                    communities they belong to.""",
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
    @PreAuthorize("isAuthenticated()")
    public PagedResult<PlantResponse> getAllPlants(@Parameter(hidden = true) Pageable page) {
        Set<UUID> visibleCommunityIds = communityAccessGuard.visibleCommunityIds();
        PagedResult<Plant> plants = service.findAllByCommunities(paginationRequestMapper.mapRequest(page),
                visibleCommunityIds);

        List<PlantResponse> plantsResponse = plants.getItems().stream()
                .map(PlantResponse::new)
                .toList();

        return new PagedResult<>(plantsResponse, plants.getSize(), plants.getTotalElements(),
                plants.getTotalPages(), plants.getNumber());
    }
}
