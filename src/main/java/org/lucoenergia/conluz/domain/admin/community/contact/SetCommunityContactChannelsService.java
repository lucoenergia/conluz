package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.List;
import java.util.UUID;

public interface SetCommunityContactChannelsService {

    /**
     * Replaces the community's whole list of contact channels with {@code drafts}, in the submitted order (CTC-002).
     *
     * @return the channels now stored, in their stored order
     * @throws ContactChannelsInvalidException if the list breaks CTC-003; nothing is stored then
     */
    List<ContactChannel> replace(UUID communityId, List<ContactChannelDraft> drafts);
}
