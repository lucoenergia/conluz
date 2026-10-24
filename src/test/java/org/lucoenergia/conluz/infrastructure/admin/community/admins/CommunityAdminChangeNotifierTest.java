package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.AdminChangeActor;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;

/**
 * The notifier itself (#466): how it reads the recipients, and that nothing of a change that rolls back is sent or
 * logged.
 */
class CommunityAdminChangeNotifierTest extends BaseCommunityAdminTest {

    @Autowired
    private CommunityAdminChangeNotifier notifier;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("MEM-035 the recipients are read with the same number of statements for 1 admin and for 5")
    void theRecipientsAreRead_withAConstantNumberOfStatements() {
        Change withOneAdmin = removalIn(newCommunity(), 1);
        Change withFiveAdmins = removalIn(newCommunity(), 5);

        long forOne = statementsToNotify(withOneAdmin);
        awaitNextEmails(1 + 1);
        long forFive = statementsToNotify(withFiveAdmins);
        awaitNextEmails(5 + 1);

        assertThat(forOne).as("statements for one admin").isPositive();
        assertThat(forFive).as("statements for five admins").isEqualTo(forOne);
    }

    @Test
    @DisplayName("MEM-035 a change that rolls back after being notified sends no email and logs no event line")
    void aChangeThatRollsBack_sendsAndLogsNothing() throws Exception {
        Community community = newCommunity();
        Change rolledBack = removalIn(community, 1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifier.notify(CommunityAdminChange.REMOVED, before(rolledBack), null,
                    AdminChangeActor.person(UserId.of(rolledBack.actor().getId())));
            status.setRollbackOnly();
        });

        assertThat(eventLines()).isEmpty();
        // Its emails were handed to the sender, which drops them with the transaction: none is waited for
        clearInvocations(emailSender);

        // A change in another community that commits afterwards: its emails are the only ones that leave
        Change committed = removalIn(newCommunity(), 1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notifier.notify(CommunityAdminChange.REMOVED, before(committed), null,
                        AdminChangeActor.person(UserId.of(committed.actor().getId()))));
        MimeMessage[] sent = awaitNextEmails(2);
        for (MimeMessage email : sent) {
            assertThat(bodyOf(email)).contains(committed.community().getName())
                    .doesNotContain(community.getName());
        }
        assertThat(eventLines()).containsExactly("Admin change notified: communityId="
                + committed.community().getId() + ", userId=" + committed.target().getId()
                + ", change=REMOVED, actorId=" + committed.actor().getId() + ", emails=2");
    }

    // --- Fixtures ---

    private record Change(Community community, User actor, User target) {
    }

    /**
     * An admin removing another, in a community with {@code otherAdmins} more admins to tell.
     */
    private Change removalIn(Community community, int otherAdmins) {
        User actor = admin(community);
        for (int i = 0; i < otherAdmins; i++) {
            admin(community);
        }
        return new Change(community, actor, admin(community));
    }

    private User admin(Community community) {
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        remember(admin.getPersonalId());
        remember(admin.getPhoneNumber());
        return admin;
    }

    private CommunityMembership before(Change change) {
        return getMembershipsRepository.findByUserIdAndCommunityId(change.target().getId(),
                change.community().getId()).orElseThrow();
    }

    private long statementsToNotify(Change change) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long[] statements = new long[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CommunityMembership before = before(change);
            statistics.clear();
            notifier.notify(CommunityAdminChange.REMOVED, before, null,
                    AdminChangeActor.person(UserId.of(change.actor().getId())));
            statements[0] = statistics.getPrepareStatementCount();
        });
        return statements[0];
    }

    private List<String> eventLines() {
        return logs.all().stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Admin change notified:"))
                .toList();
    }
}
