package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

/**
 * Why a login failed. Recorded in the server log only: the client receives the same response for every reason,
 * so that it cannot tell a disabled account from a wrong password or an unknown personal ID.
 */
public enum LoginFailureReason {
    BAD_CREDENTIALS,
    DISABLED
}
