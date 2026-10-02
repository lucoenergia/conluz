package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Returns the currently authenticated user basic information
 */
@RestController
@RequestMapping("/api/v1/users/current")
public class GetCurrentUserController {

    private final UserCapabilitiesAssembler capabilitiesAssembler;
    private final PlatformCapabilitiesAssembler platformCapabilitiesAssembler;

    public GetCurrentUserController(UserCapabilitiesAssembler capabilitiesAssembler,
                                    PlatformCapabilitiesAssembler platformCapabilitiesAssembler) {
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.platformCapabilitiesAssembler = platformCapabilitiesAssembler;
    }

    @GetMapping
    @Operation(
            summary = "Get current authenticated user",
            description = "Returns basic information about the currently authenticated user.",
            tags = ApiTag.USERS,
            operationId = "getCurrentUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @UnauthorizedErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        // The principal already carries its memberships -- UserDetailsServiceFromDatabase attaches
        // them on every request -- so nothing is loaded here.
        return ResponseEntity.ok(new CurrentUserResponse(currentUser,
                capabilitiesAssembler.assembleWithLoadedMemberships(currentUser, currentUser),
                platformCapabilitiesAssembler.assemble(currentUser)));
    }
}
