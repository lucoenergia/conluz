package org.lucoenergia.conluz.infrastructure.shared.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.lucoenergia.conluz.infrastructure.shared.email.EmailTestData.*;

/**
 * Sends through senders built for each test against an embedded or a misbehaving SMTP server, inside the
 * application's real transactions. Each sender has a single thread, so emails go out in the order they were
 * handed over.
 */
class EmailSenderTest extends BaseIntegrationTest {

    private static final String LOCALHOST = "127.0.0.1";
    private static final Duration DELIVERY = Duration.ofSeconds(10);

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EmailSender applicationEmailSender;

    private TransactionTemplate transaction;
    private GreenMail greenMail;
    private LogCapture logs;
    private final List<AfterCommitEmailSender> senders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort())
                .withConfiguration(GreenMailConfiguration.aConfig().withUser(USERNAME, USERNAME, PASSWORD));
        greenMail.start();
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void tearDown() {
        senders.forEach(AfterCommitEmailSender::destroy);
        greenMail.stop();
        try {
            assertNoSensitiveData(logs.all());
        } finally {
            logs.close();
        }
    }

    @Test
    void sendsAnEmailRequestedInATransactionOnceItCommits() throws Exception {
        EmailSender sender = senderFor(enabledProperties(LOCALHOST, greenMail.getSmtp().getPort()));

        transaction.executeWithoutResult(status -> sender.send(email("COMMITTED")));

        assertThat(greenMail.waitForIncomingEmail(DELIVERY.toMillis(), 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    void neverSendsAnEmailRequestedInATransactionThatRollsBack() throws Exception {
        EmailSender sender = senderFor(enabledProperties(LOCALHOST, greenMail.getSmtp().getPort()));
        String rolledBackSubject = "Rolled back";
        String committedSubject = "Committed afterwards";

        transaction.executeWithoutResult(status -> {
            sender.send(email("ROLLED_BACK", rolledBackSubject));
            status.setRollbackOnly();
        });
        // Sent after the rolled-back one on the same single thread: once it has arrived, the first would have too.
        transaction.executeWithoutResult(status -> sender.send(email("COMMITTED", committedSubject)));

        assertThat(greenMail.waitForIncomingEmail(DELIVERY.toMillis(), 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo(committedSubject);
    }

    @Test
    void sendsAnEmailRequestedOutsideATransactionAtOnce() {
        EmailSender sender = senderFor(enabledProperties(LOCALHOST, greenMail.getSmtp().getPort()));

        sender.send(email("NO_TRANSACTION"));

        assertThat(greenMail.waitForIncomingEmail(DELIVERY.toMillis(), 1)).isTrue();
    }

    @Test
    void anUnreachableServerLeavesTheTransactionUnaffectedAndLogsOneWarning() throws IOException {
        EmailSender sender = senderFor(enabledProperties(LOCALHOST, unusedPort()));

        String result = transaction.execute(status -> {
            sender.send(email("UNREACHABLE"));
            return "completed";
        });

        assertThat(result).isEqualTo("completed");
        assertOneFailureWarning("Email UNREACHABLE not sent: MailSendException");
    }

    @Test
    void aRejectingServerLeavesTheTransactionUnaffectedAndLogsOneWarning() throws IOException {
        try (RawSmtpServer server = RawSmtpServer.rejecting()) {
            EmailSender sender = senderFor(enabledProperties(LOCALHOST, server.port()));

            String result = transaction.execute(status -> {
                sender.send(email("REJECTED"));
                return "completed";
            });

            assertThat(result).isEqualTo("completed");
            assertOneFailureWarning("Email REJECTED not sent: MailSendException");
            assertThat(server.connections()).isEqualTo(1);
        }
    }

    @Test
    void doesNotWaitForASlowServer() throws Exception {
        try (RawSmtpServer server = RawSmtpServer.holding()) {
            EmailSender sender = senderFor(enabledProperties(LOCALHOST, server.port()));

            // Well under the SMTP timeouts: sending on the calling thread would hold it until the server answers.
            assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    transaction.executeWithoutResult(status -> sender.send(email("SLOW_SERVER"))));

            assertThat(server.awaitConnection(DELIVERY.toSeconds())).isTrue();
            server.release();
            assertOneFailureWarning("Email SLOW_SERVER not sent: MailSendException");
        }
    }

    @Test
    void dropsAnEmailWithOneWarningWhenTheQueueIsFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Bounded, so that a sender that delivered on the calling thread would fail this test instead of hanging it
        AfterCommitEmailSender sender = register(new AfterCommitEmailSender(ignored -> {
            started.countDown();
            release.await(DELIVERY.toSeconds(), TimeUnit.SECONDS);
        }, 1, 1));

        try {
            sender.send(email("FIRST"));
            assertThat(started.await(DELIVERY.toSeconds(), TimeUnit.SECONDS)).isTrue();
            sender.send(email("QUEUED"));
            sender.send(email("DROPPED"));

            assertThat(logs.warningsAndAbove())
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getFormattedMessage())
                                .isEqualTo("Email DROPPED dropped: the sending queue is full");
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        } finally {
            release.countDown();
        }
    }

    @Test
    void withSendingDisabledNoConnectionIsOpenedAndOneLineIsLogged() throws Exception {
        try (RawSmtpServer server = RawSmtpServer.holding()) {
            EmailSender sender = senderFor(disabledProperties(LOCALHOST, server.port()));

            transaction.executeWithoutResult(status -> sender.send(email("DISABLED")));

            assertOneDisabledLine("Email DISABLED not sent: sending is disabled");
            assertThat(server.connections()).isZero();
            assertThat(logs.warningsAndAbove()).isEmpty();
        }
    }

    @Test
    void sendingIsDisabledInTheApplicationByDefault() {
        transaction.executeWithoutResult(status -> applicationEmailSender.send(email("APPLICATION_DEFAULT")));

        assertOneDisabledLine("Email APPLICATION_DEFAULT not sent: sending is disabled");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    private EmailSender senderFor(EmailProperties properties) {
        return register(new AfterCommitEmailSender(new EmailConfiguration().emailTransport(properties), 1, 10));
    }

    private AfterCommitEmailSender register(AfterCommitEmailSender sender) {
        senders.add(sender);
        return sender;
    }

    private void assertOneFailureWarning(String expected) {
        await().atMost(DELIVERY).until(() -> !emailThreadEvents(Level.WARN).isEmpty());
        assertThat(emailThreadEvents(Level.WARN))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo(expected);
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    private void assertOneDisabledLine(String expected) {
        await().atMost(DELIVERY).until(() -> !emailThreadEvents(Level.INFO).isEmpty());
        assertThat(emailThreadEvents(Level.INFO))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(expected));
    }

    private List<ILoggingEvent> emailThreadEvents(Level level) {
        return logs.all().stream()
                .filter(event -> event.getThreadName().startsWith(AfterCommitEmailSender.THREAD_NAME_PREFIX))
                .filter(event -> event.getLevel().equals(level))
                .toList();
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
