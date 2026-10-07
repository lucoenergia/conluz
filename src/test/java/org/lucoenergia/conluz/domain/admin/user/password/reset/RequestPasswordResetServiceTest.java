package org.lucoenergia.conluz.domain.admin.user.password.reset;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.password.reset.BasePasswordResetTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RequestPasswordResetServiceTest extends BasePasswordResetTest {

    private static final int ROUNDS = 10;

    @Autowired
    private RequestPasswordResetService service;

    @Test
    void concurrentRequests_neverSendMoreThanTheDailyLimit() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            User user = newUser();
            issueDirectly(user);
            issueDirectly(user);
            clearInvocations(emailSender);
            // A client address per round, so that the requests of all the rounds stay under its limit
            String clientIp = "198.51.100." + round;

            List<Object> outcomes = race(
                    () -> {
                        service.request(user.getPersonalId(), clientIp);
                        return "done";
                    },
                    () -> {
                        service.request(user.getPersonalId(), clientIp);
                        return "done";
                    });

            assertThat(outcomes).as("round %d", round).containsOnly("done");
            assertThat(tokenCount(user)).as("round %d", round).isEqualTo(RequestPasswordResetService.DAILY_LIMIT);
            verify(emailSender, times(1)).send(any());
        }
        awaitEmails(ROUNDS);
    }
}
