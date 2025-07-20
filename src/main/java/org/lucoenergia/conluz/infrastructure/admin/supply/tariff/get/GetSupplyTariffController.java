package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for managing supply tariffs
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class GetSupplyTariffController {

    private final GetSupplyTariffService service;

    public GetSupplyTariffController(GetSupplyTariffService service) {
        this.service = service;
    }

    @GetMapping("/supplies/{id}/tariffs")
    @Operation(
            summary = "Gets tariff values for a supply",
            description = """
                This endpoint retrieves the tariff values for a supply by specifying the supply's unique identifier in the endpoint path.
                
                Authentication through an authentication token is required for secure access.
                
                Only the owner of the supply or an admin user can view tariff values for a supply.
                
                If no tariff values have been set for the supply, a 404 Not Found response is returned.""",
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyTariff"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Tariff values successfully retrieved.",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    public SupplyTariffResponse getSupplyTariff(@PathVariable("id") UUID supplyId) {
        return service.getTariffBySupplyId(SupplyId.of(supplyId))
                .map(SupplyTariffResponse::new)
                .orElseThrow(() -> new SupplyTariffNotFoundException(supplyId));
    }
}