package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.notification.CommunityAdminChangeEmailFactory;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseSendingMemberTest;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending, resending and revoking invitations, and bulk sending them, on a server that can send (#393).
 *
 * <p>Declares no properties of its own, so its subclasses share the context of every {@link BaseSendingMemberTest}.
 * Emails are counted as they arrive: {@link #awaitNextEmails(int)} waits for the next ones and returns only them.</p>
 */
public abstract class BaseInvitationTest extends BaseSendingMemberTest {

    protected static final Pattern LINK = Pattern.compile(
            Pattern.quote(PUBLIC_WEB_URL + "/accept-invitation#") + "([A-Za-z0-9_-]+)");

    @Autowired
    protected GetOneTimeTokenService getOneTimeTokenService;

    private final Set<String> emailsSeen = new HashSet<>();
    private long sendsSeen;
    private int notificationSendsRead;

    /**
     * A pending member of a community, registered and invited by one of its admins, and the link they were sent.
     */
    public record InvitedMember(Community community, String bearer, UUID userId, String personalId,
                                   String contactEmail, String token) {
    }

    @BeforeEach
    void noEmailSeenYet() {
        emailsSeen.clear();
        sendsSeen = 0;
        notificationSendsRead = 0;
    }

    // --- Fixtures ---

    protected InvitedMember invitedMember() throws Exception {
        Community community = newCommunity();
        return invitedMemberOf(community, loginAsAdminOf(community));
    }

    protected InvitedMember invitedMemberOf(Community community, String bearer) throws Exception {
        Map<String, Object> body = newRecord();
        UUID userId = registered(bearer, community.getId(), body);
        invite(bearer, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);
        return new InvitedMember(community, bearer, userId, (String) body.get("personalId"),
                (String) body.get("contactEmail"), token);
    }

    // --- Requests ---

    protected ResultActions resend(String bearer, UUID communityId, UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invitation/resend",
                communityId, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    protected ResultActions resend(InvitedMember member) throws Exception {
        return resend(member.bearer(), member.community().getId(), member.userId());
    }

    protected ResultActions revoke(String bearer, UUID communityId, UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/invitation/revoke",
                communityId, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    protected ResultActions revoke(InvitedMember member) throws Exception {
        return revoke(member.bearer(), member.community().getId(), member.userId());
    }

    protected ResultActions sendInvitations(String bearer, UUID communityId) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/invite", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /**
     * A bulk send to the selected members (#527), with the body as given.
     */
    protected ResultActions sendInvitations(String bearer, UUID communityId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/invite", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * A bulk send to the selected members (#527), in the order given.
     */
    protected ResultActions sendInvitations(String bearer, UUID communityId, List<UUID> userIds) throws Exception {
        return sendInvitations(bearer, communityId, objectMapper.writeValueAsString(Map.of("userIds", userIds)));
    }

    // --- Emails and links ---

    /**
     * Waits for the next {@code count} emails, and returns only them. The embedded server lists its emails by
     * mailbox, not in the order they arrived, so the ones already returned are told apart by their message id.
     */
    protected MimeMessage[] awaitNextEmails(int count) {
        MimeMessage[] all = awaitEmails(emailsSeen.size() + count);
        List<MimeMessage> next = new ArrayList<>();
        for (MimeMessage email : all) {
            if (emailsSeen.add(messageId(email))) {
                next.add(email);
            }
        }
        sendsSeen += count;
        return next.toArray(MimeMessage[]::new);
    }

    /**
     * Waits for every email handed to the sender since the emails last waited for, and returns only them: a request
     * that changes a community's admins hands over the notifications of the change too (#466).
     */
    protected MimeMessage[] awaitNextEmailsHandedOver() {
        long sends = mockingDetails(emailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .count();
        return awaitNextEmails(Math.toIntExact(sends - sendsSeen));
    }

    /**
     * The notifications of changes to a community's admins (#466) handed to the sender since the last call, each
     * checked to contain no DNI, contact email or phone remembered by the test (MEM-036).
     */
    protected List<Email> nextAdminChangeNotifications() {
        List<Invocation> sends = mockingDetails(emailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .toList();
        List<Email> notifications = new ArrayList<>();
        for (Invocation send : sends.subList(notificationSendsRead, sends.size())) {
            Email email = send.getArgument(0);
            if (CommunityAdminChangeEmailFactory.CATEGORY.equals(email.category())) {
                assertNoSecretIn(email.subject() + "\n" + email.body());
                notifications.add(email);
            }
        }
        notificationSendsRead = sends.size();
        return notifications;
    }

    protected static List<String> recipients(List<Email> emails) {
        return emails.stream().map(Email::recipient).toList();
    }

    protected static String bodyTo(List<Email> emails, String recipient) {
        List<Email> to = emails.stream().filter(email -> email.recipient().equals(recipient)).toList();
        assertThat(to).as("notifications to the recipient").hasSize(1);
        return to.get(0).body();
    }

    private static String messageId(MimeMessage email) {
        try {
            return email.getMessageID();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The one email among them sent to the address.
     */
    protected static MimeMessage emailTo(MimeMessage[] emails, String address) throws Exception {
        List<MimeMessage> to = new ArrayList<>();
        for (MimeMessage email : emails) {
            if (Arrays.stream(email.getAllRecipients()).anyMatch(recipient -> recipient.toString().equals(address))) {
                to.add(email);
            }
        }
        assertThat(to).as("emails to the address").hasSize(1);
        return to.get(0);
    }

    protected String tokenFrom(MimeMessage email) throws Exception {
        Matcher matcher = LINK.matcher(bodyOf(email));
        assertThat(matcher.find()).as("the email contains an invitation link").isTrue();
        String token = matcher.group(1);
        remember(token);
        return token;
    }

    /**
     * Whether the link would be accepted now, without using it.
     */
    protected boolean linkWorks(String token) {
        return getOneTimeTokenService.findMembership(token, OneTimeTokenPurpose.INVITATION).isPresent();
    }

    // --- Rows ---

    protected Instant invitedAt(String personalId, UUID communityId) {
        Timestamp invitedAt = (Timestamp) membershipRow(personalId, communityId).get("invited_at");
        return invitedAt == null ? null : invitedAt.toInstant();
    }

    protected int invitationTokenCount(String personalId, UUID communityId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token t "
                        + "JOIN community_memberships m ON m.id = t.membership_id JOIN users u ON u.id = m.user_id "
                        + "WHERE u.personal_id = ? AND m.community_id = ? AND t.purpose = 'INVITATION'",
                Integer.class, personalId, communityId);
    }

    // --- Concurrency ---

    /**
     * Runs the tasks at the same time, each on its own thread, and returns what each returned or threw, in order.
     */
    protected static List<Object> race(List<Callable<?>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
