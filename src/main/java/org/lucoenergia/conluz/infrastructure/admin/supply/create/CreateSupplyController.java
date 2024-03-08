package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds a new supply
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateSupplyController {

    private final CreateSupplyAssembler assembler;
    private final CreateSupplyService service;

    public CreateSupplyController(CreateSupplyAssembler assembler, CreateSupplyService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new supply within the system.",
            description = "This endpoint is designed to create a new supply within the system. To utilize this endpoint, a client sends a request containing essential details such as the supply's address, partition coefficient, and any relevant parameters. Proper authentication, through authentication tokens, is required to access this endpoint. Upon successful creation, the server responds with a status code of 200, providing comprehensive details about the newly created supply, including its unique identifier. In case of failure, the server returns an appropriate error status code along with a descriptive error message, aiding the client in diagnosing and addressing the issue. This endpoint plays a pivotal role in dynamically expanding the system's repertoire of energy supplies.",
            tags = ApiTag.SUPPLIES,
            operationId = "createSupply"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The supply has been successfully created.",
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
                                               "name":null,
                                               "address":"Fake Street 123",
                                               "partitionCoefficient":3.0763,
                                               "enabled":true
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
    public SupplyResponse createSupply(@RequestBody CreateSupplyBody body) {
        Supply newSupply = service.create(assembler.assemble(body), UserPersonalId.of(body.getPersonalId()));
        return new SupplyResponse(newSupply);
    }
}
