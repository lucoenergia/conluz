package org.lucoenergia.conluz.domain.admin.community.admins.notification;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Objects;

/**
 * Who made a change to a community's admins. Whether they acted as the platform operator is decided by the path the
 * change went through, not by the roles the person holds: the platform admin changes admins through
 * {@code …/admins}, and a person of the community, a platform admin acting through a membership of its own included,
 * through the memberships or by accepting an invitation.
 *
 * @param userId           the person who made the change
 * @param platformOperator true when the change was the platform admin's, named as the operator; false when it was a
 *                         person of the community, named from their member record
 */
public record AdminChangeActor(UserId userId, boolean platformOperator) {

    public AdminChangeActor {
        Objects.requireNonNull(userId, "userId");
    }

    public static AdminChangeActor platformOperator(UserId userId) {
        return new AdminChangeActor(userId, true);
    }

    public static AdminChangeActor person(UserId userId) {
        return new AdminChangeActor(userId, false);
    }
}
