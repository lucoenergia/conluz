package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.GetInvitationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.TooManyRequestsErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the holder of an invitation link what accepting it requires (#394). Public: the token is the only
 * credential.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Validated
public class InspectInvitationController {

    private final GetInvitationService service;

    public InspectInvitationController(GetInvitationService service) {
        this.service = service;
    }

    @PostMapping("/inspect")
    @Operation(
            summary = "Inspects an invitation before accepting it",
            description = """
                This endpoint tells the holder of an invitation link, `<web client>/accept-invitation#<token>`,
                which community the invitation is for and how to accept it. It requires no authentication, and
                any token presented with it is ignored. It uses up nothing and changes nothing: the link stays
                usable.

                When `loginRequired` is false, the account has no password: the client asks for a first password
                and accepts with `acceptInvitation`. When it is true, the account already has a password: the
                client asks the person to log in and accepts with `acceptInvitationAsCurrentUser`.

                `kind` tells what the invitation is for: `COMMUNITY`, a membership of the community in
                `community`; or `PLATFORM`, the platform-admin role, with `community` null. When
                `confirmationCodeRequired` is true, as it is for every `PLATFORM` invitation, the client also asks
                for the confirmation code that whoever invited gives the person by another channel, and sends it
                as `confirmationCode` when accepting. A platform-admin invitation whose account is disabled can be
                inspected and accepted too.

                A token that is unknown, malformed, expired, already used, revoked or replaced by a newer one, whose membership
                has already been accepted, or whose account has been disabled, is answered 400 with the
                `MEMBERSHIP_INVITATION_TOKEN_INVALID` code, the same in every case.

                Every unusable token counts against the client address, together with failed logins, password
                changes and password resets: after 20 within 15 minutes, further requests are answered 429 with a
                Retry-After header until the 15 minutes that started with the first failure have passed.""",
            tags = ApiTag.MEMBERSHIPS,
            operationId = "inspectInvitation"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The invitation can be accepted",
                    useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            The body is invalid, or the token cannot be used (`MEMBERSHIP_INVITATION_TOKEN_INVALID`).""",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Invalid token",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-15T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The invitation link is invalid or has expired. Please ask your community for a new one.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The invitation link is invalid or has expired. Please ask your community for a new one.",
                                                           "code": "MEMBERSHIP_INVITATION_TOKEN_INVALID",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    public InvitationInspectionResponse inspectInvitation(@Valid @RequestBody InvitationTokenBody body,
                                                          HttpServletRequest request) {
        return new InvitationInspectionResponse(service.inspect(body.getToken(), request.getRemoteAddr()));
    }
}
