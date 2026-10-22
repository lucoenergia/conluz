package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannels;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactEmailValidity;
import org.lucoenergia.conluz.domain.admin.community.contact.LockCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class SetCommunityContactChannelsServiceImpl implements SetCommunityContactChannelsService {

    private final ContactEmailValidity contactEmailValidity;
    private final LockCommunityContactChannelsRepository lockRepository;
    private final SetCommunityContactChannelsRepository setRepository;

    public SetCommunityContactChannelsServiceImpl(ContactEmailValidity contactEmailValidity,
                                                  LockCommunityContactChannelsRepository lockRepository,
                                                  SetCommunityContactChannelsRepository setRepository) {
        this.contactEmailValidity = contactEmailValidity;
        this.lockRepository = lockRepository;
        this.setRepository = setRepository;
    }

    /**
     * Validated before anything is locked or written, so a rejected list leaves the stored one as it was. The
     * community is then locked, so that concurrent replacements run one after another and the last one wins
     * (#462 accepts last-write-wins and adds no optimistic concurrency).
     */
    @Override
    public List<ContactChannel> replace(UUID communityId, List<ContactChannelDraft> drafts) {
        List<ContactChannel> channels = ContactChannels.validate(drafts, contactEmailValidity);
        lockRepository.lock(communityId);
        return setRepository.replace(communityId, channels);
    }
}
