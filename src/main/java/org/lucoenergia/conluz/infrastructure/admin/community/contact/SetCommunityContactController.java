package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/contact",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetCommunityContactController {

    private final SetCommunityContactChannelsService service;

    public SetCommunityContactController(SetCommunityContactChannelsService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Replaces the contact channels of a community.",
            description = """
                    Replaces the whole list of the community's contact channels with the submitted one, kept in the \
                    submitted order, and returns it as stored. An empty list removes every channel. There are no \
                    partial updates: send every channel the community should keep. Concurrent replacements are \
                    applied one after another, and the last one wins.

                    Every text is trimmed before it is checked and stored, and a blank `schedule` or `notes` is \
                    stored as null. A list that breaks a rule is answered 400 with one error per broken rule, each \
                    with its own code and, for a rule about one channel, the channel's 0-based `index` in `params`; \
                    nothing is stored then.

                    Only an active community admin of the community may replace them. An active member who is not \
                    one is answered 403. Anyone else, a platform admin who is not a member of the community \
                    included, is answered 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "setCommunityContact",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The contact channels now stored, in their stored order.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The list breaks a rule, and nothing is stored. Each broken rule is one error with "
                            + "its code: `CONTACT_CHANNELS_MISSING`, `CONTACT_CHANNELS_TOO_MANY`, "
                            + "`CONTACT_CHANNEL_TYPE_INVALID`, `CONTACT_CHANNEL_LABEL_BLANK`, "
                            + "`CONTACT_CHANNEL_LABEL_TOO_LONG`, `CONTACT_CHANNEL_VALUE_BLANK`, "
                            + "`CONTACT_CHANNEL_VALUE_TOO_LONG`, `CONTACT_CHANNEL_SCHEDULE_TOO_LONG`, "
                            + "`CONTACT_CHANNEL_NOTES_TOO_LONG`, `CONTACT_CHANNEL_EMAIL_INVALID`, "
                            + "`CONTACT_CHANNEL_PHONE_CHARACTERS_INVALID` or `CONTACT_CHANNEL_PHONE_TOO_FEW_DIGITS`. "
                            + "A body that is not JSON of the documented shape is answered without a code.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    name = "phoneTooFewDigits",
                                    summary = "The phone number of the second channel has too few digits",
                                    value = """
                                            {
                                               "timestamp": "2026-10-22T10:10:25.534035352+02:00",
                                               "status": 400,
                                               "message": "Channel 2: a phone number must have at least 6 digits.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "Channel 2: a phone number must have at least 6 digits.",
                                                   "code": "CONTACT_CHANNEL_PHONE_TOO_FEW_DIGITS",
                                                   "params": {
                                                     "index": "1",
                                                     "minDigits": "6"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityContact(#communityId)")
    public CommunityContactResponse setCommunityContact(@PathVariable("communityId") UUID communityId,
                                                        @RequestBody SetCommunityContactBody body) {
        return CommunityContactResponse.of(service.replace(communityId, body.toDrafts()));
    }
}
