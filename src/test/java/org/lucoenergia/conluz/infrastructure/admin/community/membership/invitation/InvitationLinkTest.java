package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationLinkTest {

    private static final RawOneTimeToken TOKEN = new RawOneTimeToken("Ab_9-xY");

    @ParameterizedTest
    @CsvSource({
            "https://app.example.org, https://app.example.org/accept-invitation#Ab_9-xY",
            "https://app.example.org//, https://app.example.org/accept-invitation#Ab_9-xY",
            "https://example.org/conluz/, https://example.org/conluz/accept-invitation#Ab_9-xY"
    })
    void putsTheTokenInTheFragment_withoutTheTrailingSlashes(String publicUrl, String expected) {
        InvitationLink link = InvitationLink.of(publicUrl);

        assertThat(link.isConfigured()).isTrue();
        assertThat(link.forToken(TOKEN)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "app.example.org", "ftp://app.example.org", "https://app.example.org/?x=1"})
    void anythingButAnAbsoluteHttpUrl_isNotConfigured(String publicUrl) {
        InvitationLink link = InvitationLink.of(publicUrl);

        assertThat(link.isConfigured()).isFalse();
        assertThat(link.forToken(TOKEN)).isEmpty();
    }
}
