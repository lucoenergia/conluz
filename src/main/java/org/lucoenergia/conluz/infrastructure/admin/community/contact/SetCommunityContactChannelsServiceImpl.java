package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelsValidator;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class SetCommunityContactChannelsServiceImpl implements SetCommunityContactChannelsService {

    private final ContactChannelsValidator contactChannelsValidator;
    private final LockCommunityRepository lockRepository;
    private final SetCommunityContactChannelsRepository setRepository;

    public SetCommunityContactChannelsServiceImpl(ContactChannelsValidator contactChannelsValidator,
                                                  LockCommunityRepository lockRepository,
                                                  SetCommunityContactChannelsRepository setRepository) {
        this.contactChannelsValidator = contactChannelsValidator;
        this.lockRepository = lockRepository;
        this.setRepository = setRepository;
    }

    /**
     * Validated before anything is locked or written, so a rejected list leaves the stored one as it was. The
     * community is then locked, so that concurrent replacements run one after another and the last one wins
     * (#462 accepts last-write-wins and adds no optimistic concurrency). Without the lock, two replacements deleting
     * the same rows at once would both insert their channels, and the second would fail on the position of the
     * first instead of replacing it. The community exists: the guard has resolved it before this is called.
     */
    @Override
    public List<ContactChannel> replace(UUID communityId, List<ContactChannelDraft> drafts) {
        List<ContactChannel> channels = contactChannelsValidator.validate(drafts);
        lockRepository.lock(communityId);
        return setRepository.replace(communityId, channels);
    }
}
