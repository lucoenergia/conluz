package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementPartitionsService;
import org.lucoenergia.conluz.domain.admin.supply.get.SupplyPartitionWithComparison;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionWithComparisonResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/sharing-agreements", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementPartitionsController {

    private final GetSharingAgreementPartitionsService service;

    public GetSharingAgreementPartitionsController(GetSharingAgreementPartitionsService service) {
        this.service = service;
    }

    @GetMapping("/{id}/supply-partitions")
    @Operation(
            summary = "Gets supply partitions for a sharing agreement",
            description = "Returns the supply partitions for a sharing agreement, including comparison with the " +
                    "previous agreement's coefficients.",
            tags = ApiTag.SUPPLIES,
            operationId = "getSharingAgreementPartitions",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply partitions retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupplyPartitionWithComparisonResponse> getPartitions(@PathVariable("id") UUID id) {
        List<SupplyPartitionWithComparison> partitions =
                service.findPartitions(SharingAgreementId.of(id));
        return partitions.stream()
                .map(SupplyPartitionWithComparisonResponse::new)
                .toList();
    }
}
