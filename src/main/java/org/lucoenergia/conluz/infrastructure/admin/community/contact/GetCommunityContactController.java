package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.PasswordChangeRequiredErrorResponse;
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
        value = "/api/v1/communities/{communityId}/contact",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityContactController {

    private final GetCommunityContactChannelsService service;

    public GetCommunityContactController(GetCommunityContactChannelsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the contact channels of a community.",
            description = """
                    Returns how the members of the community can contact it: its phone numbers, email addresses \
                    and postal addresses, in the order the community admins listed them. The list is empty if the \
                    community has none.

                    Only the active members of the community and platform admins may read them; anyone else is \
                    answered 404, so the response does not reveal whether the community exists.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityContact",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The community's contact channels, in their stored order.",
                    useReturnTypeSchema = true
            )
    })
    @PasswordChangeRequiredErrorResponse
    @UnauthorizedErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadCommunity(#communityId)")
    public CommunityContactResponse getCommunityContact(@PathVariable("communityId") UUID communityId) {
        return CommunityContactResponse.of(service.findByCommunityId(communityId));
    }
}
