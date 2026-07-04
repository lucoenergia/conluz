package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GrantPlatformAdminController {

    private final ManagePlatformAdminAccessService service;

    public GrantPlatformAdminController(ManagePlatformAdminAccessService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{id}/grant-platform-admin")
    @Operation(
            summary = "Grants platform-admin privileges to a user by ID",
            description = """
                    This endpoint promotes the user identified in the endpoint path to platform administrator.

                    Proper authentication, through an authentication token, is required for secure access.
                    **Required: Platform Admin.**

                    The operation is idempotent: granting the flag to a user who is already a platform admin has no effect.

                    Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user is now a platform administrator.

                    In situations where the operation encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to assist clients in diagnosing and resolving the issue.""",
            tags = ApiTag.USERS,
            operationId = "grantPlatformAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Platform-admin privileges granted successfully"
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void grantPlatformAdmin(@PathVariable("id") UUID userId) {
        service.grant(UserId.of(userId));
    }
}
