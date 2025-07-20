package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffService;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRequest;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for setting supply tariffs
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class SetSupplyTariffController {

    private final SetSupplyTariffService service;

    public SetSupplyTariffController(SetSupplyTariffService service) {
        this.service = service;
    }

    @PutMapping("/supplies/{id}/tariffs")
    @Operation(
            summary = "Sets tariff values for a supply",
            description = """
                This endpoint enables setting tariff values for a supply by specifying the supply's unique identifier in the endpoint path.
                
                Clients send a request containing the tariff values (valley, peak, off-peak), and authentication through an authentication token is required for secure access.
                
                Only the owner of the supply or an admin user can set tariff values for a supply.
                
                A successful update results in an HTTP status code of 200, indicating that the tariff values have been successfully set. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.""",
            tags = ApiTag.SUPPLIES,
            operationId = "setSupplyTariff"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Tariff values successfully set.",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    public SupplyTariffResponse setSupplyTariff(@PathVariable("id") UUID supplyId, @Valid @RequestBody SupplyTariffRequest request) {
        SupplyTariff supplyTariff = service.setTariff(request.mapToSupplyTariff(supplyId));
        return new SupplyTariffResponse(supplyTariff);
    }
}