package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registers a new partition coefficient for a supply, closing the previously active period.
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies/{supplyId}/partition-coefficients",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class RegisterPartitionCoefficientController {

    private final PartitionCoefficientService service;

    public RegisterPartitionCoefficientController(PartitionCoefficientService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Registers a new partition coefficient for a supply.",
            description = """
                    Closes the currently active coefficient period and opens a new one starting at effectiveAt.
                    Also updates the supply.partitionCoefficient denormalization field.
                    The response includes a communityCoefficientSumWarning field if the total sum of active
                    coefficients across all supplies at effectiveAt deviates from 100 by more than 0.0001.
                    This warning is informational only — the change is always persisted.
                    **Required Role: ADMIN**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "registerPartitionCoefficient",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Coefficient registered successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public RegisterPartitionCoefficientResponse registerCoefficient(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId,
            @Valid @RequestBody RegisterPartitionCoefficientBody body) {

        SupplyPartitionCoefficient saved = service.registerCoefficientChange(
                supplyId, body.getCoefficient(), body.getEffectiveAt());

        BigDecimal communitySum = service.computeCommunitySum(body.getEffectiveAt());
        return new RegisterPartitionCoefficientResponse(saved, communitySum);
    }
}
