package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.admin.supply.get.GetAllSharingAgreementsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementListResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/sharing-agreements", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetAllSharingAgreementsController {

    private final GetAllSharingAgreementsService service;
    private final SupplyPartitionRepository supplyPartitionRepository;

    public GetAllSharingAgreementsController(GetAllSharingAgreementsService service,
                                             SupplyPartitionRepository supplyPartitionRepository) {
        this.service = service;
        this.supplyPartitionRepository = supplyPartitionRepository;
    }

    @GetMapping
    @Operation(
            summary = "Gets all sharing agreements",
            description = "Returns a list of all sharing agreements ordered by start date descending, " +
                    "including counts of active and previous agreements and partition stats per agreement.",
            tags = ApiTag.SUPPLIES,
            operationId = "getAllSharingAgreements",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreements retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public SharingAgreementListResponse getAllSharingAgreements() {
        List<SharingAgreement> agreements = service.findAll();

        List<SharingAgreementResponse> items = agreements.stream()
                .map(agreement -> {
                    List<SupplyPartitionEntity> partitions =
                            supplyPartitionRepository.findBySharingAgreementId(agreement.getId());
                    return new SharingAgreementResponse(agreement, partitions);
                })
                .toList();

        int activeCount = (int) items.stream()
                .filter(r -> r.getStatus() == SharingAgreementStatus.ACTIVE)
                .count();
        int previousCount = items.size() - activeCount;

        return new SharingAgreementListResponse(items.size(), activeCount, previousCount, items);
    }
}
