package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UpdateMemberRecordController {

    private final UpdateMemberRecordService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public UpdateMemberRecordController(UpdateMemberRecordService service,
                                        MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PutMapping("/{userId}/record")
    @Operation(
            summary = "Replaces the member record of a membership.",
            description = """
                    Replaces the record this community keeps on a member: full name, member number, contact email, \
                    address and phone number. The whole record is sent: an optional value that is missing, null, \
                    empty or blank is cleared. Only the record is written, never the person's account, which they \
                    share with their other communities, nor the membership's role or state. The only other change \
                    is the invitation link revoked as described below.

                    Any membership of the community may be edited, whatever its role and whether it is pending, \
                    accepted or disabled, the caller's own included. A user with no membership in the community \
                    responds 404.

                    A membership with the `COMMUNITY_ADMIN` role must keep a contact email: a record without one \
                    responds 400 with the `MEMBERSHIP_WITHOUT_CONTACT_EMAIL` code, and nothing is changed.

                    Changing the contact email of a pending membership revokes its active invitation link, so a link \
                    sent to the old address stops working and `invitationExpiresAt` is null; the next invitation, or \
                    resend, goes to the new contact email. A change only of letter case is not a change of address \
                    and keeps the link.

                    **Required: Community Admin of this community.** A platform admin who is not one is refused with \
                    403.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "updateMemberRecord",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Member record updated",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canEditMemberRecord(#communityId)")
    public MemberResponse updateMemberRecord(@AuthenticationPrincipal User currentUser,
                                             @PathVariable("communityId") UUID communityId,
                                             @PathVariable("userId") UUID userId,
                                             @Valid @RequestBody UpdateMemberRecordBody body) {
        MembershipWithInvitation updated = service.update(communityId, UserId.of(userId), body.toMemberRecord());
        CommunityMembership membership = updated.membership();
        return new MemberResponse(membership.getUser().getPersonalId(), membership, updated.invitationExpiresAt(),
                capabilitiesAssembler.assemble(currentUser, membership));
    }
}
