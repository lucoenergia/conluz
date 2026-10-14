package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.infrastructure.shared.web.PublicWebUrl;

import java.util.Optional;

/**
 * Builds the link an invitation email points to: {@code <public web URL>/accept-invitation#<token>}. The token goes
 * in the fragment, which browsers never send to any server, so it reaches no access log on the way.
 */
public final class InvitationLink {

    static final String PATH = "/accept-invitation";

    private final PublicWebUrl publicWebUrl;

    private InvitationLink(PublicWebUrl publicWebUrl) {
        this.publicWebUrl = publicWebUrl;
    }

    /**
     * Accepts only what {@link PublicWebUrl#of(String)} accepts. Anything else is treated as not configured.
     */
    static InvitationLink of(String publicUrl) {
        return new InvitationLink(PublicWebUrl.of(publicUrl));
    }

    public boolean isConfigured() {
        return publicWebUrl.isConfigured();
    }

    /**
     * @return the link for the token, or empty if the public web URL is not configured
     */
    public Optional<String> forToken(RawOneTimeToken token) {
        return publicWebUrl.linkWithToken(PATH, token);
    }
}
