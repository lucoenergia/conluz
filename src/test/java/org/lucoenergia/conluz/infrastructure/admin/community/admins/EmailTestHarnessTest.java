package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import com.icegreen.greenmail.util.GreenMailUtil;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The email test harness itself (#571): a test waits only for the emails it dispatched, and reads only its own.
 * Shares its context with the other invitation tests.
 */
class EmailTestHarnessTest extends BaseCommunityAdminTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aSendWhoseTransactionRollsBack_isNotWaitedFor() {
        long start = System.nanoTime();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            emailSender.send(new Email(newEmail(), "Never sent", "Rolled back", new EmailCategory("HARNESS")));
            transaction.setRollbackOnly();
        });

        assertThat(awaitNextEmailsHandedOver()).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(DELIVERY_MILLIS));
    }

    @Test
    void aFixture_findsItsOwnEmail_withAStrayEmailInTheMailbox() throws Exception {
        GreenMailUtil.sendTextEmail(newEmail(), "stray@conluz.test", "Stray", "Not an invitation",
                GREEN_MAIL.getSmtp().getServerSetup());

        InvitedAdmin admin = invitedAdmin();

        assertThat(linkWorks(admin.token())).isTrue();
    }
}
