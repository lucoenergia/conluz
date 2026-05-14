package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/sharing-agreements")
public class GetSharingAgreementController {

    private final GetSharingAgreementService service;
    private final SupplyPartitionRepository supplyPartitionRepository;

    public GetSharingAgreementController(GetSharingAgreementService service,
                                         SupplyPartitionRepository supplyPartitionRepository) {
        this.service = service;
        this.supplyPartitionRepository = supplyPartitionRepository;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Gets a sharing agreement by ID",
            description = "This endpoint retrieves a sharing agreement by its unique identifier.",
            tags = ApiTag.SUPPLIES,
            operationId = "getSharingAgreement"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    public SharingAgreementResponse getSharingAgreement(@PathVariable("id") UUID id) {
        SharingAgreement sharingAgreement = service.getById(SharingAgreementId.of(id));
        List<SupplyPartitionEntity> partitions = supplyPartitionRepository.findBySharingAgreementId(id);
        return new SharingAgreementResponse(sharingAgreement, partitions);
    }
}
