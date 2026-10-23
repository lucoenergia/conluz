package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.List;
import java.util.UUID;

public interface GetCommunityContactChannelsRepository {

    /**
     * @return the community's contact channels in their stored order, or an empty list if it has none
     */
    List<ContactChannel> findByCommunityId(UUID communityId);
}
