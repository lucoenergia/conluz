package org.lucoenergia.conluz.infrastructure.production.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production")
public class GetInstantProductionController {

    private final GetProductionService getProductionService;

    public GetInstantProductionController(GetProductionService getProductionService) {
        this.getProductionService = getProductionService;
    }

    @GetMapping
    @Operation(
            summary = "Delivers real-time energy production details for a specific power plant supply.",
            description = "This endpoint offers real-time insights into the instantaneous energy production of a designated power plant supply, identified by its unique supply ID. Clients must authenticate using an authentication token. Upon a successful request, the server responds with an HTTP status code of 200, furnishing up-to-the-moment production metrics for the specified supply. In cases of errors or invalid parameters, the server issues an appropriate error status code accompanied by descriptive messages. This endpoint proves invaluable for immediate monitoring and analysis of energy output, enabling timely decision-making and performance evaluation for the designated power plant supply.",
            tags = ApiTag.PRODUCTION,
            operationId = "getInstantProduction"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """

                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public InstantProduction getInstantProduction(@RequestParam(required = false) UUID supplyId) {
        if (Objects.isNull(supplyId)) {
            return getProductionService.getInstantProduction();
        }
        return getProductionService.getInstantProductionBySupply(SupplyId.of(supplyId));
    }
}
