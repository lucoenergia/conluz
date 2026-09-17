package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.profile.UpdateProfileService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a user change their own contact details.
 *
 * <p>Separate from {@code PUT /users/{userId}} on purpose. That endpoint is an administrative one —
 * it replaces the identifying fields too — and widening its guard to admit self-edits would have
 * handed every member the ability to rewrite their own DNI and member number. This endpoint has no
 * user id to point anywhere: it acts on the caller, which is why {@code isAuthenticated()} is the
 * whole rule and no object-scoped guard method exists for it.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class UpdateProfileController {

    private final UpdateProfileService service;

    public UpdateProfileController(UpdateProfileService service) {
        this.service = service;
    }

    @PutMapping("/users/profile")
    @Operation(
            summary = "Updates the contact details of the current user",
            description = """
                This endpoint lets the authenticated user change how the community reaches them: email
                (required), postal address and phone number. It always acts on the caller — there is no
                user identifier to supply — so it cannot be used to edit anybody else.
                
                Name, DNI and member number are not editable here: they identify the member to the
                community and to the distributor, so changing them is an administrative operation
                performed through `PUT /api/v1/users/{userId}`.
                
                Omitting `address` or `phoneNumber` clears the stored value; `email` is mandatory.
                
                **Required: any authenticated user (edits their own contact details).**""",
            tags = ApiTag.USERS,
            operationId = "updateProfile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Contact details updated successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateProfile(@AuthenticationPrincipal User currentUser,
                                      @Valid @RequestBody UpdateProfileBody body) {
        return new UserResponse(
                service.updateContactDetails(UserId.of(currentUser.getId()), body.toContactDetails()));
    }
}
