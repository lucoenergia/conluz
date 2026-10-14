package org.lucoenergia.conluz.infrastructure.shared.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;

import static org.assertj.core.api.Assertions.assertThat;

class PublicWebUrlTest {

    private static final RawOneTimeToken TOKEN = new RawOneTimeToken("Ab_9-xY");

    @ParameterizedTest
    @CsvSource({
            "https://app.example.org, https://app.example.org/page#Ab_9-xY",
            "https://app.example.org/, https://app.example.org/page#Ab_9-xY",
            "https://app.example.org///, https://app.example.org/page#Ab_9-xY",
            "' https://app.example.org/ ', https://app.example.org/page#Ab_9-xY",
            "http://localhost:3001, http://localhost:3001/page#Ab_9-xY",
            "https://example.org/conluz/, https://example.org/conluz/page#Ab_9-xY",
            "HTTPS://app.example.org, HTTPS://app.example.org/page#Ab_9-xY",
            "HtTp://app.example.org, HtTp://app.example.org/page#Ab_9-xY"
    })
    void anAbsoluteHttpUrl_isConfiguredAndLinksWithoutItsTrailingSlashes(String publicUrl, String expected) {
        PublicWebUrl url = PublicWebUrl.of(publicUrl);

        assertThat(url.isConfigured()).isTrue();
        assertThat(url.linkWithToken("/page", TOKEN)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "/",
            "app.example.org",
            "/reset",
            "ftp://app.example.org",
            "mailto:someone@example.org",
            "javascript:alert(1)",
            "https://",
            "https:///path",
            "https://app.example.org/?x=1",
            "https://app.example.org?x=1",
            "https://app.example.org/#top",
            "https://app.example.org#",
            "https://app example.org"
    })
    void anythingButAnAbsoluteHttpUrlWithAHostAndNoQueryOrFragment_isNotConfigured(String publicUrl) {
        PublicWebUrl url = PublicWebUrl.of(publicUrl);

        assertThat(url.isConfigured()).isFalse();
        assertThat(url.linkWithToken("/page", TOKEN)).isEmpty();
    }

    /**
     * The token goes only in the fragment, after the path, so no request a browser makes to the link carries it.
     */
    @Test
    void theTokenGoesOnlyInTheFragmentAfterThePath() {
        String link = PublicWebUrl.of("https://app.example.org").linkWithToken("/accept-invitation", TOKEN)
                .orElseThrow();

        assertThat(link).isEqualTo("https://app.example.org/accept-invitation#Ab_9-xY");
        assertThat(link.substring(0, link.indexOf('#'))).doesNotContain(TOKEN.value());
    }
}
