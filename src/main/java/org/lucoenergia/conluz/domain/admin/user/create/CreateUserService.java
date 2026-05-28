package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

public interface CreateUserService {

    User create(User user);

    User create(User user, UUID communityId, CommunityRole communityRole);
}
