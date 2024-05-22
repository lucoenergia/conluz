package org.lucoenergia.conluz.infrastructure.production.plant.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds a new plant
 */
@RestController
@RequestMapping(
        value = "/api/v1/plants",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreatePlantController {

    private final CreatePlantService service;

    public CreatePlantController(CreatePlantService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new plant within the system.",
            description = "This endpoint is designed to create a new plant within the system. To utilize this endpoint, a client sends a request containing essential details such as the plants's address, its code and any relevant parameters. Proper authentication, through authentication tokens, is required to access this endpoint. Upon successful creation, the server responds with a status code of 200, providing comprehensive details about the newly created plant, including its unique identifier. In case of failure, the server returns an appropriate error status code along with a descriptive error message, aiding the client in diagnosing and addressing the issue.",
            tags = ApiTag.PLANTS,
            operationId = "createPlant"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The plant has been successfully created.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "id":"785de77b-8c22-4d2f-9d12-f172113f9aa4",
                                               "code":"ES0033333333333333AA0A",
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
                                               "connectionDate":"2024/05/23"
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    public PlantResponse createPlant(@Valid @RequestBody CreatePlantBody body) {
        Plant newPlant = service.create(body.mapToPlant(), SupplyCode.of(body.getSupplyCode()));
        return new PlantResponse(newPlant);
    }
}
