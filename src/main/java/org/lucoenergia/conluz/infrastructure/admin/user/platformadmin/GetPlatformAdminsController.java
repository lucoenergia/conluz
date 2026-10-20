package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminListService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdminListEntry;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformAdminCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
        value = "/api/v1/platform-admins",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlatformAdminsController {

    private final GetPlatformAdminListService service;
    private final PlatformAdminCapabilitiesAssembler capabilitiesAssembler;

    public GetPlatformAdminsController(GetPlatformAdminListService service,
                                       PlatformAdminCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @GetMapping
    @Operation(
            summary = "Lists the platform admins.",
            description = """
                    Every user with the platform-admin role, enabled or not, by full name and then id: the user id, \
                    and the full name, email and state of their own account. No personal id, no member record and \
                    no memberships. Each one says whether revoking the role would be accepted right now, and \
                    whether the caller may revoke it. Only a platform admin may list them.

                    They are followed by the pending invitations to the role, by the full name typed by the \
                    inviter and then id: the user id, the full name and email typed by the inviter, nothing from \
                    the account, and `invitation` with when it was sent and when its link expires, null once \
                    it has expired or been revoked. `enabled` and `revocable` are null on those rows, and \
                    `invitation` is null on the others. A pending invitation never counts as a platform admin, \
                    for `revocable` or anything else, and the list never shows a confirmation code.
                    """,
            tags = ApiTag.USERS,
            operationId = "getPlatformAdmins",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The platform admins",
                    useReturnTypeSchema = true
            )
    })
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("@communityAccessGuard.canListPlatformAdmins()")
    public List<PlatformAdminResponse> getPlatformAdmins(@AuthenticationPrincipal User currentUser) {
        return service.findAll().stream()
                .map(entry -> toResponse(entry, currentUser))
                .toList();
    }

    private PlatformAdminResponse toResponse(PlatformAdminListEntry entry, User currentUser) {
        if (entry instanceof PlatformAdmin admin) {
            return new PlatformAdminResponse(admin, capabilitiesAssembler.assemble(currentUser, admin.user().getId()));
        }
        PendingPlatformAdmin pending = (PendingPlatformAdmin) entry;
        return new PlatformAdminResponse(pending, capabilitiesAssembler.assemble(currentUser, pending.userId()));
    }
}
