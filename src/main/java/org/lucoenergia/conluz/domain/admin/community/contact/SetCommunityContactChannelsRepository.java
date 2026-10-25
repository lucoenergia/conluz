package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.List;
import java.util.UUID;

public interface SetCommunityContactChannelsRepository {

    /**
     * Replaces every contact channel of the community with {@code channels}, stored in the order given. Takes the
     * caller's transaction, which must hold the community's lock
     * ({@link org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository}).
     *
     * @return the channels now stored, in their stored order
     */
    List<ContactChannel> replace(UUID communityId, List<ContactChannel> channels);
}
