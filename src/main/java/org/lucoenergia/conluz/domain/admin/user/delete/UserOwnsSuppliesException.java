package org.lucoenergia.conluz.domain.admin.user.delete;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * The user owns at least one supply, in any community, so their account is not deleted (ACC-009). A supply carries
 * the community's energy data and sharing agreements: it is removed deliberately, never as a side effect of deleting
 * its owner.
 */
public class UserOwnsSuppliesException extends RuntimeException {

    private final UserId userId;

    public UserOwnsSuppliesException(UserId userId) {
        this.userId = userId;
    }

    public UserId getUserId() {
        return userId;
    }
}
