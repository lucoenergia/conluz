package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.accept.BaseInvitationAcceptanceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class AcceptInvitationServiceTest extends BaseInvitationAcceptanceTest {

    private static final int ROUNDS = 10;

    @Autowired
    private AcceptInvitationService acceptInvitationService;

    @Test
    @DisplayName("MEM-019 accepting and resending concurrently for the same membership never deadlock, and end consistently")
    void acceptingAndResending_concurrently_neverDeadlock() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            InvitedMember member = invitedMember();
            String password = newPassword();
            String clientIp = "198.51.100." + (100 + round);

            List<Object> outcomes = race(List.of(
                    () -> {
                        acceptInvitationService.acceptWithNewPassword(member.token(), password, clientIp);
                        return "accepted";
                    },
                    () -> resend(member).andReturn().getResponse().getStatus()));

            assertConsistent(member, outcomes, "round " + round);
        }
    }

    /**
     * The resend takes the user row first, and the acceptance tries to take it only then, which is the interleaving
     * where taking the membership row before the user row would deadlock.
     */
    @Test
    @DisplayName("MEM-019 an acceptance that finds the user row taken by a resend waits for it, and does not deadlock")
    void anAcceptanceBehindAResend_waitsForIt_andDoesNotDeadlock() throws Exception {
        InvitedMember member = invitedMember();
        String password = newPassword();
        CountDownLatch resendHoldsTheUser = new CountDownLatch(1);
        AtomicReference<Thread> acceptance = new AtomicReference<>();
        doAnswer(invocation -> {
            if (Thread.currentThread() == acceptance.get()) {
                assertThat(resendHoldsTheUser.await(10, TimeUnit.SECONDS)).isTrue();
                return invocation.callRealMethod();
            }
            Object locked = invocation.callRealMethod();
            resendHoldsTheUser.countDown();
            return locked;
        }).when(spyOf(lockUserRepository)).lock(any());

        List<Object> outcomes = race(List.of(
                () -> {
                    acceptance.set(Thread.currentThread());
                    acceptInvitationService.acceptWithNewPassword(member.token(), password, "198.51.100.200");
                    return "accepted";
                },
                () -> resend(member).andReturn().getResponse().getStatus()));

        // The resend went first, so it replaced the link the acceptance presented
        assertThat(outcomes.get(1)).isEqualTo(200);
        assertThat(outcomes.get(0)).isInstanceOf(InvitationTokenInvalidException.class);
        assertConsistent(member, outcomes, "forced interleaving");
    }

    /**
     * The spy itself, behind the transactional proxy, so that stubbing it does not start a transaction.
     */
    private static <T> T spyOf(T bean) {
        return AopTestUtils.getUltimateTargetObject(bean);
    }

    private void assertConsistent(InvitedMember member, List<Object> outcomes, String description) throws Exception {
        UUID communityId = member.community().getId();
        Object accepted = outcomes.get(0);
        Object resent = outcomes.get(1);
        if ("accepted".equals(accepted)) {
            // The resend found the membership accepted
            assertThat(resent).as(description).isEqualTo(409);
            assertThat(acceptedAt(member.personalId(), communityId)).as(description).isNotNull();
            assertThat(accountRow(member.personalId()).get("password")).as(description).isNotNull();
            assertThat(getOneTimeTokenService.findValidExpiryForMembership(
                    UserId.of(member.userId()), communityId,
                    OneTimeTokenPurpose.INVITATION))
                    .as(description).isEmpty();
        } else {
            // The resend replaced the link first, so the acceptance found it unusable and changed nothing
            assertThat(accepted).as(description).isInstanceOf(InvitationTokenInvalidException.class);
            assertThat(resent).as(description).isEqualTo(200);
            assertThat(acceptedAt(member.personalId(), communityId)).as(description).isNull();
            assertThat(accountRow(member.personalId()).get("password")).as(description).isNull();
            String newToken = tokenFrom(awaitNextEmails(1)[0]);
            assertThat(linkWorks(newToken)).as(description).isTrue();
        }
    }
}
