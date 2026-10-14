package org.lucoenergia.conluz.infrastructure.shared.web;

import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * The public web URL that emailed links point to, validated once. Links carry their one-time token in the fragment,
 * which browsers never send to any server, so it reaches no access log on the way.
 */
public final class PublicWebUrl {

    private final String baseUrl;

    private PublicWebUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Accepts only an absolute {@code http} or {@code https} URL with a host and no query or fragment, without the
     * trailing slashes. Anything else is treated as not configured.
     */
    public static PublicWebUrl of(String publicUrl) {
        return new PublicWebUrl(normalise(publicUrl));
    }

    public boolean isConfigured() {
        return baseUrl != null;
    }

    /**
     * @param path the path of the page, starting with a slash
     * @return {@code <public web URL><path>#<token>}, or empty if the public web URL is not configured
     */
    public Optional<String> linkWithToken(String path, RawOneTimeToken token) {
        if (baseUrl == null) {
            return Optional.empty();
        }
        // The token is URL-safe Base64, so it needs no encoding
        return Optional.of(baseUrl + path + "#" + token.value());
    }

    private static String normalise(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String trimmed = publicUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return null;
            }
            return trimmed;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
