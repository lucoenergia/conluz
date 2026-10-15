package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseSendingMemberTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending, resending and revoking invitations, and bulk sending them, on a server that can send (#393).
 *
 * <p>Declares no properties of its own, so its subclasses share the context of every {@link BaseSendingMemberTest}.
 * Emails are counted as they arrive: {@link #awaitNextEmails(int)} waits for the next ones and returns only them.</p>
 */
abstract class BaseInvitationTest extends BaseSendingMemberTest {

    protected static final Pattern LINK = Pattern.compile(
            Pattern.quote(PUBLIC_WEB_URL + "/accept-invitation#") + "([A-Za-z0-9_-]+)");

    @Autowired
    protected GetOneTimeTokenService getOneTimeTokenService;

    private int emailsSeen;

    /**
     * A pending member of a community, registered and invited by one of its admins, and the link they were sent.
     */
    protected record InvitedMember(Community community, String bearer, UUID userId, String personalId,
                                   String contactEmail, String token) {
    }

    @BeforeEach
    void noEmailSeenYet() {
        emailsSeen = 0;
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

    // --- Emails and links ---

    /**
     * Waits for the next {@code count} emails, and returns only them.
     */
    protected MimeMessage[] awaitNextEmails(int count) {
        MimeMessage[] all = awaitEmails(emailsSeen + count);
        MimeMessage[] next = Arrays.copyOfRange(all, emailsSeen, all.length);
        emailsSeen = all.length;
        return next;
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
