package org.lucoenergia.conluz.domain.admin.user.token;

/**
 * What a token of a purpose is bound to, beyond its user. Each scope has its own operations in the token services,
 * and its own rule of at most one active token.
 */
public enum OneTimeTokenScope {

    /**
     * Bound only to the user: at most one active token per user and purpose.
     */
    USER,

    /**
     * Bound to a membership of the user: at most one active token per membership, whatever its purpose.
     */
    MEMBERSHIP
}
