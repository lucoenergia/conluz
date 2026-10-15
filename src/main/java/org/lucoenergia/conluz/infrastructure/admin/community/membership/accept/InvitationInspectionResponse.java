package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationInspection;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;

@Schema(requiredProperties = {"community", "loginRequired"})
public class InvitationInspectionResponse {

    @Schema(description = "The community the invitation is for")
    private final CommunityReferenceResponse community;

    @Schema(description = """
            Whether the person must log in to accept the invitation: true when the account already has a \
            password, so the invitation is accepted through `acceptInvitationAsCurrentUser` after logging in; \
            false when it has none, so it is accepted through `acceptInvitation`, choosing its first password.""")
    private final boolean loginRequired;

    public InvitationInspectionResponse(InvitationInspection inspection) {
        this.community = new CommunityReferenceResponse(inspection.communityId(), inspection.communityName());
        this.loginRequired = inspection.loginRequired();
    }

    public CommunityReferenceResponse getCommunity() {
        return community;
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }
}
