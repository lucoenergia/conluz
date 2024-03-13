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
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springdoc.core.converters.models.PageableAsQueryParam;
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

    public GetAllSuppliesController(GetSupplyService service) {
        this.service = service;
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
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "items":[
                                                  {
                                                     "id":"123e4567-e89b-12d3-a456-426614174000",
                                                     "code":"hhtc50AmS9KRqvZuYecV",
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
                                                     "name":"My house",
                                                     "address":"Fake Street 123",
                                                     "partitionCoefficient":1.5156003E38,
                                                     "enabled":true
                                                  },
                                                  {
                                                     "id":"3f4fb466-2753-4f6d-bf73-8478e876e50b",
                                                     "code":"mbHX0arnmS4KgooidQxj",
                                                     "user":{
                                                         "id":"81b4de42-a49f-440f-868a-f1cf72199ae7",
                                                         "personalId":"j3E44iio",
                                                         "number":12,
                                                         "fullName":"Alice Cooper",
                                                         "address":"Main Street 123",
                                                         "email":"alice@HDXvc.com",
                                                         "phoneNumber":"+34666555444",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                     },
                                                     "name":"Main street 123",
                                                     "address":"Main street 123",
                                                     "partitionCoefficient":2.4035615E38,
                                                     "enabled":true
                                                  },
                                                  {
                                                     "id":"785de77b-8c22-4d2f-9d12-f172113f9aa4",
                                                     "code":"6OOtEWtt4a0epeugj1y2",
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
                                                     "name":"The village house",
                                                     "address":"Real street 22",
                                                     "partitionCoefficient":2.4804912E38,
                                                     "enabled":true
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
    public PagedResult<SupplyResponse> getAllSupplies(@Parameter(hidden = true) PagedRequest page) {
        PagedResult<Supply> supplies = service.findAll(page);

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(SupplyResponse::new)
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }
}
