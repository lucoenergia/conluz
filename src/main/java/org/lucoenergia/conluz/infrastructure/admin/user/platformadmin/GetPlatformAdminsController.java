package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsService;
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

    private final GetPlatformAdminsService service;
    private final PlatformAdminCapabilitiesAssembler capabilitiesAssembler;

    public GetPlatformAdminsController(GetPlatformAdminsService service,
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
                .map(admin -> new PlatformAdminResponse(admin,
                        capabilitiesAssembler.assemble(currentUser, admin.user().getId())))
                .toList();
    }
}
