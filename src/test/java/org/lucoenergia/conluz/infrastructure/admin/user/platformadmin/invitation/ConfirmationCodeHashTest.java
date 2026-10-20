package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationCodeHashTest {

    private static final UUID INVITATION = UUID.randomUUID();

    @Test
    @DisplayName("ACC-015 only a hash is stored, and the same code hashes differently on two invitations")
    void theHashIsNotTheCode_andDependsOnTheInvitation() {
        ConfirmationCode code = ConfirmationCode.generate();

        String hash = ConfirmationCodeHash.of(INVITATION, code);

        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain(code.value());
        assertThat(ConfirmationCodeHash.of(UUID.randomUUID(), code)).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("ACC-016 the right code matches as typed, and a wrong or missing one does not")
    void onlyTheRightCodeMatches() {
        ConfirmationCode code = new ConfirmationCode("7K3MQ9XD");
        String hash = ConfirmationCodeHash.of(INVITATION, code);

        assertThat(ConfirmationCodeHash.matches(INVITATION, "7k3m-q9xd", hash)).isTrue();
        assertThat(ConfirmationCodeHash.matches(INVITATION, "7K3MQ9XE", hash)).isFalse();
        assertThat(ConfirmationCodeHash.matches(UUID.randomUUID(), "7K3MQ9XD", hash)).isFalse();
        assertThat(ConfirmationCodeHash.matches(INVITATION, null, hash)).isFalse();
        assertThat(ConfirmationCodeHash.matches(INVITATION, "", hash)).isFalse();
    }
}
