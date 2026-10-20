package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeInvalidException;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two requests on the same invitation, ordered with the lock wait of {@link LockWaitTestSupport}: the first holds its
 * transaction open until the second is blocked on the account's lock.
 */
class PlatformAdminInvitationConcurrencyTest extends BasePlatformAdminInvitationTest {

    @Autowired
    private AcceptInvitationService acceptInvitationService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("MEM-019 OTT-004 OTT-011 ACC-014 two acceptances of the same invitation at the same time: exactly one "
            + "grants the role")
    void twoAcceptances_onlyOneGrantsTheRole() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        String first = newPassword();
        String second = newPassword();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> acceptInvitationService.acceptWithNewPassword(invitation.token(), first, invitation.code(),
                        "192.0.2.190"),
                () -> acceptInvitationService.acceptWithNewPassword(invitation.token(), second, invitation.code(),
                        "192.0.2.191"));

        assertThat(secondOutcome).isInstanceOf(InvitationTokenInvalidException.class);
        assertThat(isPlatformAdmin(invitation.userId())).isTrue();
        // The first acceptance's password is the one set
        bearerOf(login(invitation.personalId(), first));
        login(invitation.personalId(), second).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ? "
                + "AND used_at IS NOT NULL", Integer.class, invitation.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ACC-016 two wrong codes at the same time are both counted")
    void twoWrongCodes_areBothCounted() throws Exception {
        PlatformInvitation invitation = invited(loginAsPlatformAdmin());
        String wrong = wrongCode(invitation.code());
        String password = newPassword();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                // Caught inside the transaction, so that it commits the count as a request's own transaction does
                () -> assertThrows(ConfirmationCodeInvalidException.class, () -> acceptInvitationService
                        .acceptWithNewPassword(invitation.token(), password, wrong, "192.0.2.192")),
                () -> acceptInvitationService.acceptWithNewPassword(invitation.token(), password, wrong,
                        "192.0.2.193"));

        assertThat(secondOutcome).isInstanceOf(ConfirmationCodeInvalidException.class);
        assertThat(failedCodeAttempts(invitation.userId())).isEqualTo(2);
        assertThat(isPlatformAdmin(invitation.userId())).isFalse();
        assertThat(platformLinkWorks(invitation.token())).isTrue();
    }
}
