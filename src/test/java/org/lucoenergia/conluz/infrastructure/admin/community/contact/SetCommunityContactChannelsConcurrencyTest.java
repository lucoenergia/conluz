package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelType;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.threeChannels;

/**
 * Two replacements of the same community's channels, ordered with the lock wait of {@link LockWaitTestSupport}: the
 * first holds its transaction open until the second is blocked. Not transactional itself, so that each replacement
 * commits as a request's own transaction does.
 */
class SetCommunityContactChannelsConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private SetCommunityContactChannelsService setCommunityContactChannelsService;
    @Autowired
    private GetCommunityContactChannelsRepository getCommunityContactChannelsRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Without the lock, the second replacement would find nothing left to delete once the first committed, and its
     * inserts would then collide with the first's on their positions.
     */
    @Test
    @DisplayName("CTC-002 two replacements of the same list at the same time both succeed, and the last one wins")
    void twoReplacementsAtTheSameTime_bothSucceed_andTheLastOneWins() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        setCommunityContactChannelsService.replace(community.getId(), threeChannels());

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> setCommunityContactChannelsService.replace(community.getId(), List.of(
                        new ContactChannelDraft("PHONE", "First", "600 000 001", null, null),
                        new ContactChannelDraft("EMAIL", "First", "first@example.org", null, null))),
                () -> setCommunityContactChannelsService.replace(community.getId(), List.of(
                        new ContactChannelDraft("ADDRESS", "Second", "Calle Mayor 2", null, null),
                        new ContactChannelDraft("PHONE", "Second", "600 000 002", null, null),
                        new ContactChannelDraft("EMAIL", "Second", "second@example.org", null, null))));

        assertThat(secondOutcome).isNull();
        assertThat(getCommunityContactChannelsRepository.findByCommunityId(community.getId())).containsExactly(
                new ContactChannel(ContactChannelType.ADDRESS, "Second", "Calle Mayor 2", null, null),
                new ContactChannel(ContactChannelType.PHONE, "Second", "600 000 002", null, null),
                new ContactChannel(ContactChannelType.EMAIL, "Second", "second@example.org", null, null));
    }
}
