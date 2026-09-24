package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipInvestmentService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sets and clears the investment recorded on a membership.
 *
 * <p>Both operations answer 204 with no body. The membership resource is deliberately not
 * returned: an investment is personal financial data, and echoing it back would put the amount in
 * a response shape that the memberships endpoints also use, which is the first step towards it
 * leaking somewhere it is not guarded. The value is read back through the payback endpoint, under
 * that endpoint's own authorization.
 */
@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships/{userId}/investment",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetMembershipInvestmentController {

    private final UpdateMembershipInvestmentService service;

    public SetMembershipInvestmentController(UpdateMembershipInvestmentService service) {
        this.service = service;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Records a membership's initial investment.",
            description = """
                    Sets the amount, in euros, that this member initially contributed to the
                    community, replacing any amount previously recorded. Only the current value is
                    kept: there is no history of changes.

                    The amount must be greater than zero with at most two decimals. Requires
                    COMMUNITY_ADMIN of this community; platform admins who do not administer this
                    community are not granted access, and are answered 404 rather than 403 so the
                    membership's existence is not disclosed.

                    Answers 204 with no body. Read the stored amount back through the membership's
                    payback endpoint.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "setMembershipInvestment",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Investment recorded successfully"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMembershipInvestment(#communityId)")
    public void setMembershipInvestment(@PathVariable("communityId") UUID communityId,
                                        @PathVariable("userId") UUID userId,
                                        @Valid @RequestBody SetMembershipInvestmentBody body) {
        service.setInvestment(communityId, userId, body.getInvestmentEur());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Removes a membership's initial investment.",
            description = """
                    Clears the amount recorded for this member, returning the membership to having
                    no investment. Payback then reports no investment rather than an investment of
                    zero, which are different statements.

                    Idempotent: clearing a membership that has no investment succeeds, because the
                    end state is the one the caller asked for. Requires COMMUNITY_ADMIN of this
                    community, with the same 404-not-403 mapping as the write.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "clearMembershipInvestment",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Investment removed successfully"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMembershipInvestment(#communityId)")
    public void clearMembershipInvestment(@PathVariable("communityId") UUID communityId,
                                          @PathVariable("userId") UUID userId) {
        service.clearInvestment(communityId, userId);
    }
}
