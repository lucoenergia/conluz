package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationInspection;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationKind;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;

@Schema(requiredProperties = {"kind", "community", "loginRequired", "confirmationCodeRequired"})
public class InvitationInspectionResponse {

    @Schema(description = """
            What the invitation is for: `COMMUNITY`, a membership of the community in `community`; or \
            `PLATFORM`, the platform-admin role.""")
    private final InvitationKind kind;

    @Schema(description = "The community the invitation is for. Null for a `PLATFORM` invitation.",
            types = {"object", "null"})
    private final CommunityReferenceResponse community;

    @Schema(description = """
            Whether the person must log in to accept the invitation: true when the account already has a \
            password, so the invitation is accepted through `acceptInvitationAsCurrentUser` after logging in; \
            false when it has none, so it is accepted through `acceptInvitation`, choosing its first password.""")
    private final boolean loginRequired;

    @Schema(description = """
            Whether accepting requires the confirmation code that whoever invited gives the person by another \
            channel: true for a `PLATFORM` invitation, false for a `COMMUNITY` one. The code is never sent by \
            email, and no endpoint returns it to the person invited.""")
    private final boolean confirmationCodeRequired;

    public InvitationInspectionResponse(InvitationInspection inspection) {
        this.kind = inspection.kind();
        this.community = inspection.communityId() == null ? null
                : new CommunityReferenceResponse(inspection.communityId(), inspection.communityName());
        this.loginRequired = inspection.loginRequired();
        this.confirmationCodeRequired = inspection.confirmationCodeRequired();
    }

    public InvitationKind getKind() {
        return kind;
    }

    public CommunityReferenceResponse getCommunity() {
        return community;
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }

    public boolean isConfirmationCodeRequired() {
        return confirmationCodeRequired;
    }
}
