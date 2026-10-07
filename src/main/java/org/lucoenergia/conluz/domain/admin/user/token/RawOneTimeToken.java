package org.lucoenergia.conluz.domain.admin.user.token;

/**
 * A one-time token as handed to its user. It is a secret: it is returned once, when it is issued, and is never
 * stored or logged. {@link #toString()} does not print it.
 *
 * @param value the token, URL-safe
 */
public record RawOneTimeToken(String value) {

    public RawOneTimeToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A one-time token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RawOneTimeToken[value=<redacted>]";
    }
}
