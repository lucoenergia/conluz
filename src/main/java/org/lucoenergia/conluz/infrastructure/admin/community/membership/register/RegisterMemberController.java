package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
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
@RequestMapping(
        value = "/api/v1/communities/{communityId}/members",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class RegisterMemberController {

    private final RegisterMemberService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public RegisterMemberController(RegisterMemberService service,
                                    MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping
    @Operation(
            summary = "Registers a member in a community.",
            description = """
                    Registers a person as a member of the community with their DNI/NIE/NIF and the community's \
                    own member record. The membership is created pending, with the role COMMUNITY_MEMBER: it \
                    grants no access until the member accepts it. Only an active community admin of the \
                    community may register members; a platform admin may not.

                    If the member record has a contact email, the member is emailed an invitation to accept the \
                    membership. The link is valid for 7 days. No invitation is sent when there is no contact \
                    email, or when the server has no public web URL configured; `invitedAt` then stays null.

                    The response is the same whether or not the person already had an account, and carries \
                    nothing from it. Registering someone who already has a membership in this community, in any \
                    state, responds 409 with the `MEMBERSHIP_ALREADY_EXISTS` code.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "registerMember",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Member registered successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The person already has a membership in this community.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a member of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a member of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                                   "code": "MEMBERSHIP_ALREADY_EXISTS",
                                                   "params": {
                                                     "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                     "communityId": "5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canRegisterMembers(#communityId)")
    public MemberResponse registerMember(@AuthenticationPrincipal User currentUser,
                                         @PathVariable("communityId") UUID communityId,
                                         @Valid @RequestBody RegisterMemberBody body) {
        RegisteredMember member = service.register(communityId, UserId.of(currentUser.getId()),
                body.toPersonalId(), body.toMemberRecord());
        return new MemberResponse(member, capabilitiesAssembler.assemble(currentUser, member.membership()));
    }
}
