package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/v1/sharing-agreements")
@Transactional(readOnly = true)
public class ExportSharingAgreementController {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SupplyPartitionRepository supplyPartitionRepository;

    public ExportSharingAgreementController(SharingAgreementRepository sharingAgreementRepository,
                                             SupplyPartitionRepository supplyPartitionRepository) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.supplyPartitionRepository = supplyPartitionRepository;
    }

    @GetMapping("/{id}/supply-partitions/export")
    @Operation(
            summary = "Exports supply partitions for a sharing agreement as a TXT file",
            description = "Downloads the partition coefficients for a sharing agreement as a plain-text file " +
                    "with one line per supply in CUPS;coefficient format.",
            tags = ApiTag.SUPPLIES,
            operationId = "exportSharingAgreementPartitions",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File exported successfully"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> exportPartitions(@PathVariable("id") UUID id) {
        sharingAgreementRepository.findById(id)
                .orElseThrow(() -> new SharingAgreementNotFoundException(SharingAgreementId.of(id)));

        List<SupplyPartitionEntity> partitions = supplyPartitionRepository.findBySharingAgreementId(id);

        String content = partitions.stream()
                .map(p -> p.getSupply().getCode() + ";" + String.format("%.6f", p.getCoefficient()))
                .collect(Collectors.joining("\n"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"partitions-" + id + ".txt\"")
                .body(content);
    }
}
