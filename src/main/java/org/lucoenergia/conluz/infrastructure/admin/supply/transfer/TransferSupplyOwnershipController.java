package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.transfer.TransferSupplyOwnershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberDataAssembler;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPlantAssembler;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TransferSupplyOwnershipController {

    private final TransferSupplyOwnershipService service;
    private final SupplyCapabilitiesAssembler capabilitiesAssembler;
    private final MemberDataAssembler memberDataAssembler;
    private final SupplyPlantAssembler supplyPlantAssembler;

    public TransferSupplyOwnershipController(TransferSupplyOwnershipService service,
                                             SupplyCapabilitiesAssembler capabilitiesAssembler,
                                             MemberDataAssembler memberDataAssembler,
                                             SupplyPlantAssembler supplyPlantAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.memberDataAssembler = memberDataAssembler;
        this.supplyPlantAssembler = supplyPlantAssembler;
    }

    @PostMapping(path = "/supplies/{supplyId}/ownership/transfer")
    @Operation(
            summary = "Transfers a supply to another member of its community",
            description = """
                    Makes another member of the supply's community its owner, for example when the holder of the \
                    supply changes at the distributor. The new owner receives the supply's whole history: every \
                    read through the supply, and every figure of the new owner's membership, includes the \
                    periods before the transfer. The previous owner stops reading the supply, and their \
                    membership's figures no longer include it, also for the periods they owned it. Every transfer \
                    is recorded with the previous and the new owner, the effective date, who made it and when.

                    **Required: Community Admin of the supply's community.** The supply's owner is answered 403. \
                    Anyone who cannot see the supply, a platform admin included, is answered 404.

                    The new owner identified by `newOwnerPersonalId` must have a membership in the supply's \
                    community, in any state: pending and disabled memberships count. A new owner who has none is \
                    reported as not found (404, `SUPPLY_OWNER_NOT_FOUND`), exactly like a `newOwnerPersonalId` \
                    that matches no user. Transferring the supply to its current owner is answered 409 \
                    `SUPPLY_ALREADY_OWNED_BY_USER`.

                    The `effectiveDate` cannot be later than today in the community's time zone (400 \
                    `SUPPLY_TRANSFER_DATE_IN_FUTURE`), and must be later than the effective date of the supply's \
                    previous transfer (409 `SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS`). When the request is \
                    refused, nothing is changed.

                    From the next Datadis synchronisation, the supply's consumption is requested with the new \
                    owner's DNI/NIE/NIF.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "transferSupplyOwnership",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply transferred successfully. The response is the supply with its new owner.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The new owner already owns the supply, or the effective date is not later than the date of the supply's previous transfer. Nothing is changed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Already owned by the user",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "This member already owns the supply, so there is nothing to transfer.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "This member already owns the supply, so there is nothing to transfer.",
                                                           "code": "SUPPLY_ALREADY_OWNED_BY_USER",
                                                           "params": {}
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Date not after the previous transfer",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "The transfer date must be later than the date of the previous transfer of the supply.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The transfer date must be later than the date of the previous transfer of the supply.",
                                                           "code": "SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS",
                                                           "params": {}
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canTransferSupplyOwnership(#supplyId)")
    public SupplyResponse transferSupplyOwnership(@AuthenticationPrincipal User currentUser,
                                                  @PathVariable("supplyId") UUID supplyId,
                                                  @Valid @RequestBody TransferSupplyOwnershipBody body) {
        Supply transferred = service.transfer(SupplyId.of(supplyId),
                UserPersonalId.of(body.getNewOwnerPersonalId()), body.getEffectiveDate(),
                UserId.of(currentUser.getId()));
        return new SupplyResponse(transferred, capabilitiesAssembler.assemble(currentUser, transferred),
                memberDataAssembler.recordOfSupplyOwner(transferred), supplyPlantAssembler.plantOf(transferred));
    }
}
