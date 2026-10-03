package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

public interface CreateUserService {

    /**
     * Creates a user whose password was chosen by the user themselves, so it is not flagged as having to be
     * changed. Only the initial admin created by {@code /init} is created this way.
     */
    User create(User user);

    /**
     * Creates a user whose password was chosen by whoever creates them, so the user is flagged as having to
     * change it.
     */
    User create(User user, UUID communityId, CommunityRole communityRole);

    /**
     * Creates a user from a row of a bulk import. The row is only ever applied to the community of the
     * import: a blank or absent row community means the import community, while a row community that is
     * not a valid identifier or differs from the import community is rejected before anything is created.
     * Like {@link #create(User, UUID, CommunityRole)}, the user is flagged as having to change their password.
     *
     * @throws ImportRowCommunityMismatchException if the row community does not match the import community
     */
    User createFromImport(User user, String rowCommunityId, UUID importCommunityId, CommunityRole communityRole);
}
