package org.lucoenergia.conluz.domain.admin.user.auth;

/**
 * Why a token presented on a request was rejected. Recorded in the server log only: the client receives the same
 * 401 response for every reason.
 */
public enum TokenRejectionReason {
    /** The signature does not match the token's content under the application's key. */
    INVALID_SIGNATURE,
    /** The token cannot be parsed: wrong number of segments, invalid encoding, or a header or payload that is not JSON. */
    MALFORMED,
    /** The token's expiry is in the past. */
    EXPIRED,
    /** The token is unsigned, or signed with an algorithm the application's key cannot verify. */
    UNSUPPORTED,
    /** A claim every issued token carries is absent: subject, token id or expiry. */
    MISSING_CLAIMS,
    /** A claim holds a value no issued token carries, such as a subject that is not a user id. */
    INVALID_CLAIMS,
    /** The token was revoked: its owner logged out with it or changed their password with it. */
    REVOKED,
    /** No user exists with the token's subject. */
    USER_NOT_FOUND,
    /** The token's subject is not the user it was checked against. */
    SUBJECT_MISMATCH,
    /** The token's user is disabled. */
    USER_DISABLED,
    /** The token was issued before its user's last password change. */
    ISSUED_BEFORE_PASSWORD_CHANGE,
    /** The token was issued before its user was last disabled. */
    ISSUED_BEFORE_DISABLE
}
