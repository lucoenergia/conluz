package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Get all supplies registered in the energy community
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetAllSuppliesController {

    private final GetSupplyService service;
    private final PaginationRequestMapper paginationRequestMapper;

    public GetAllSuppliesController(GetSupplyService service, PaginationRequestMapper paginationRequestMapper) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
    }


    @GetMapping
    @Operation(
            summary = "Retrieves all registered supplies in the system with support for pagination, filtering, and sorting.",
            description = "This endpoint serves to retrieve all registered supplies within the system, supporting pagination, filtering, and sorting for a customized query experience. This endpoint requires authentication through a Bearer Token for secure access. Clients can include optional query parameters such as page to specify the page number, limit to determine supplies per page, filter to selectively retrieve supplies based on criteria, and sort to define the order of the results. A successful request yields a paginated list of supplies, providing essential details, while any authentication or retrieval issues prompt an appropriate error response. With its versatile functionality, this endpoint enhances the ability to explore and manage the array of energy supplies within the system.",
            tags = ApiTag.SUPPLIES,
            operationId = "getAllSupplies"
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
    public PagedResult<SupplyResponse> getAllSupplies(@Parameter(hidden = true) Pageable page) {
        PagedResult<Supply> supplies = service.findAll(paginationRequestMapper.mapRequest(page));

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(SupplyResponse::new)
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }
}
