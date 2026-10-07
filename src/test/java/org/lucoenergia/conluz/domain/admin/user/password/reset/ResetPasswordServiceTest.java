package org.lucoenergia.conluz.domain.admin.user.password.reset;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.password.reset.BasePasswordResetTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordServiceTest extends BasePasswordResetTest {

    private static final int ROUNDS = 20;

    @Autowired
    private ResetPasswordService resetService;
    @Autowired
    private RequestPasswordResetService requestService;

    /**
     * A reset and a new request for the same user both lock the user row before the token rows, so one waits for the
     * other instead of deadlocking. Whichever goes first, the outcome is consistent: either the reset succeeds and the
     * request then issues a new link, or the request revokes the token first and the reset is refused.
     */
    @Test
    void aResetAndANewRequestForTheSameUser_neverDeadlock() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            User user = newUser();
            String token = issueDirectly(user);
            String newPassword = newPassword();
            String clientIp = "198.51.100." + round;

            List<Object> outcomes = race(
                    () -> {
                        resetService.reset(token, newPassword, clientIp);
                        return "reset";
                    },
                    () -> {
                        requestService.request(user.getPersonalId(), clientIp);
                        return "requested";
                    });

            assertThat(outcomes.get(1)).as("round %d", round).isEqualTo("requested");
            Object reset = outcomes.get(0);
            if (reset instanceof Exception) {
                assertThat(reset).as("round %d", round).isInstanceOf(PasswordResetTokenInvalidException.class);
                assertThat(userRow(user).get("password_changed_at")).isNull();
            } else {
                assertThat(reset).isEqualTo("reset");
                assertThat(userRow(user).get("password_changed_at")).isNotNull();
            }
            // Either way the request issued a new link, the only one still usable
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ? "
                    + "AND used_at IS NULL AND revoked_at IS NULL", Integer.class, user.getId())).isEqualTo(1);
        }
    }
}
