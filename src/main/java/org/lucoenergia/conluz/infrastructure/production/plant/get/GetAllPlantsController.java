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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Get all plants registered in the energy community
 */
@RestController
@RequestMapping(value = "/api/v1/plants")
public class GetAllPlantsController {

    private final GetPlantService service;
    private final PaginationRequestMapper paginationRequestMapper;

    public GetAllPlantsController(GetPlantService service, PaginationRequestMapper paginationRequestMapper) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves all registered plants in the system with support for pagination, filtering, and sorting.",
            description = "This endpoint serves to retrieve all registered plants within the system, supporting pagination, filtering, and sorting for a customized query experience. This endpoint requires authentication through a Bearer Token for secure access. Clients can include optional query parameters such as page to specify the page number, limit to determine plants per page, filter to selectively retrieve plants based on criteria, and sort to define the order of the results. A successful request yields a paginated list of plants, providing essential details, while any authentication or retrieval issues prompt an appropriate error response. With its versatile functionality, this endpoint enhances the ability to explore and manage the array of energy plants within the system.",
            tags = ApiTag.PLANTS,
            operationId = "getAllPlants"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "items":[
                                                  {
                                                      "id":"785de77b-8c22-4d2f-9d12-f172113f9aa4",
                                                      "code":"TS-239487",
                                                      "user":{
                                                         "id":"e7ab39cd-9250-40a9-b829-f11f65aae27d",
                                                         "personalId":"rAtjrSXAU",
                                                         "number":646650705,
                                                         "fullName":"John Doe",
                                                         "address":"Fake Street 123",
                                                         "email":"DzQaM@HDXvc.com",
                                                         "phoneNumber":"+34666333111",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                      },
                                                      "name":"Solar plant 1",
                                                      "address":"Fake Street 123",
                                                      "description":"The first solar plant we installed",
                                                      "inverterProvider": "Huawei",
                                                      "totalPower":"60",
                                                      "connectionDate":"2024/01/23"
                                                  },
                                                  {
                                                      "id":"dee217f7-dd3e-4dc2-9be9-9cebca17a3ae",
                                                      "code":"TS-3240598",
                                                      "user":{
                                                         "id":"e7ab39cd-9250-40a9-b829-f11f65aae27d",
                                                         "personalId":"rAtjrSXAU",
                                                         "number":646650705,
                                                         "fullName":"John Doe",
                                                         "address":"Fake Street 123",
                                                         "email":"DzQaM@HDXvc.com",
                                                         "phoneNumber":"+34666333111",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                      },
                                                      "name":"Solar plant 2",
                                                      "address":"Fake Street 456",
                                                      "description":"The second solar plant we installed",
                                                      "inverterProvider": "Huawei",
                                                      "totalPower":"30",
                                                      "connectionDate":"2024/03/23"
                                                  },
                                                  {
                                                      "id":"f6331a5a-6f11-4439-90f2-e7ecee854c40",
                                                      "code":"TE-234987",
                                                      "user":{
                                                         "id":"e7ab39cd-9250-40a9-b829-f11f65aae27d",
                                                         "personalId":"rAtjrSXAU",
                                                         "number":646650705,
                                                         "fullName":"John Doe",
                                                         "address":"Fake Street 123",
                                                         "email":"DzQaM@HDXvc.com",
                                                         "phoneNumber":"+34666333111",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                      },
                                                      "name":"Solar plant 3",
                                                      "address":"Fake Street 789",
                                                      "description":"The last solar plant we installed",
                                                      "inverterProvider": "Huawei",
                                                      "totalPower":"40",
                                                      "connectionDate":"2024/05/23"
                                                  }
                                               ],
                                               "size":10,
                                               "totalElements":3,
                                               "totalPages":1,
                                               "number":0
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PageableAsQueryParam
    public PagedResult<PlantResponse> getAllPlants(@Parameter(hidden = true) Pageable page) {
        PagedResult<Plant> plants = service.findAll(paginationRequestMapper.mapRequest(page));

        List<PlantResponse> plantsResponse = plants.getItems().stream()
                .map(PlantResponse::new)
                .toList();

        return new PagedResult<>(plantsResponse, plants.getSize(), plants.getTotalElements(),
                plants.getTotalPages(), plants.getNumber());
    }
}
