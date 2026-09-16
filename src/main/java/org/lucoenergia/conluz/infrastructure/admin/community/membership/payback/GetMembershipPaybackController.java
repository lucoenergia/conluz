package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.GetMembershipPaybackService;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.MembershipPayback;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships/{userId}/payback",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipPaybackController {

    private final GetMembershipPaybackService service;

    public GetMembershipPaybackController(GetMembershipPaybackService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves a membership's payback progress.",
            description = """
                    How much of the member's recorded investment their share of the community's
                    energy has recovered, and roughly how long the rest would take.

                    `savedEur` is the estimated value of the self-consumed energy of **all** the
                    member's supplies in this community, from `startDate` until now, priced per
                    tariff segment with taxes included. It is computed on each request and never
                    stored, so it reflects both new consumption and any later correction. Supplies
                    in the member's other communities are not counted.

                    `startDate` is the civil date the community first activated a partition
                    coefficient, and is therefore **community-wide rather than per member**. A
                    member who joined later has their savings divided by the community's elapsed
                    days rather than their own, so their apparent daily rate is lower than their
                    real one and `estimatedRemainingMonths` is correspondingly pessimistic. This is
                    a known limitation.

                    Null and zero mean different things throughout. A null `investmentEur` means
                    none has been recorded, not a contribution of zero; a null `savedEur` means the
                    community has never shared energy, whereas zero means it has and this member
                    consumed nothing from it. `estimatedRemainingMonths` is null whenever no rate
                    can be established, and 0 once the investment is recovered.

                    `progressRatio` is not capped and exceeds 1 for a member who has recovered more
                    than they contributed. `tariffSource` is never null and is `ESTIMATE` whenever
                    any part of the amount came from an estimated tariff, or when no tariff was
                    consulted at all.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipPayback",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payback progress retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadMembershipPayback(#communityId, #userId)")
    public MembershipPaybackResponse getMembershipPayback(@PathVariable("communityId") UUID communityId,
                                                          @PathVariable("userId") UUID userId) {
        MembershipPayback payback = service.getPayback(communityId, userId);
        return new MembershipPaybackResponse(payback);
    }
}
