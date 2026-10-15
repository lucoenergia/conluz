package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationTokenResolverTest {

    private static final String TOKEN = "an-invitation-token";

    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private InvitationAttempt attempt;

    private InvitationTokenResolver resolver;
    private UserId userId;
    private UUID communityId;
    private OneTimeTokenMembership token;
    private User user;

    @BeforeEach
    void setUp() {
        resolver = new InvitationTokenResolver(getOneTimeTokenService, lockUserRepository, lockMembershipRepository,
                getUserRepository, getMembershipsRepository);
        user = UserMother.randomUser();
        user.setEnabled(true);
        userId = UserId.of(user.getId());
        communityId = UUID.randomUUID();
        token = new OneTimeTokenMembership(UUID.randomUUID(), userId, communityId);
    }

    // --- find ---

    @Test
    @DisplayName("MEM-014 a pending membership of an enabled account is usable, found without locking anything")
    void find_returnsTheUsableInvitation_withoutLocking() {
        CommunityMembership membership = membership(true, null);
        givenTheToken();
        givenTheUser(user);
        givenTheMembership(membership);

        InvitationTokenResolver.UsableInvitation found = resolver.find(TOKEN, attempt);

        assertThat(found.token()).isEqualTo(token);
        assertThat(found.user()).isSameAs(user);
        assertThat(found.membership()).isSameAs(membership);
        verifyNoInteractions(lockUserRepository, lockMembershipRepository);
        verify(attempt, never()).failed();
    }

    @Test
    @DisplayName("MEM-018 a disabled membership is usable")
    void find_acceptsADisabledMembership() {
        givenTheToken();
        givenTheUser(user);
        givenTheMembership(membership(false, null));

        assertThat(resolver.find(TOKEN, attempt).membership()).isNotNull();
        verify(attempt, never()).failed();
    }

    @Test
    @DisplayName("MEM-015 a token the store does not find is refused and counted, and nothing else is read")
    void find_refusesATokenTheStoreDoesNotFind() {
        when(getOneTimeTokenService.findMembership(TOKEN, OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.find(TOKEN, attempt)).isInstanceOf(InvitationTokenInvalidException.class);

        verify(attempt).failed();
        verifyNoInteractions(getUserRepository, getMembershipsRepository, lockUserRepository,
                lockMembershipRepository);
    }

    @Test
    @DisplayName("MEM-015 a token whose account no longer exists is refused and counted")
    void find_refusesAMissingAccount() {
        givenTheToken();
        when(getUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertRefused(() -> resolver.find(TOKEN, attempt));

        verify(getMembershipsRepository, never()).findByUserIdAndCommunityId(any(), any());
    }

    @Test
    @DisplayName("MEM-015 a token of a disabled account is refused and counted")
    void find_refusesADisabledAccount() {
        user.setEnabled(false);
        givenTheToken();
        givenTheUser(user);

        assertRefused(() -> resolver.find(TOKEN, attempt));

        verify(getMembershipsRepository, never()).findByUserIdAndCommunityId(any(), any());
    }

    @Test
    @DisplayName("MEM-015 a token whose membership no longer exists is refused and counted")
    void find_refusesAMissingMembership() {
        givenTheToken();
        givenTheUser(user);
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.empty());

        assertRefused(() -> resolver.find(TOKEN, attempt));
    }

    @ParameterizedTest(name = "membership enabled: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("MEM-015 a token of an accepted membership is refused and counted, whether or not it is enabled")
    void find_refusesAnAcceptedMembership(boolean enabled) {
        givenTheToken();
        givenTheUser(user);
        givenTheMembership(membership(enabled, Instant.parse("2026-01-01T00:00:00Z")));

        assertRefused(() -> resolver.find(TOKEN, attempt));
    }

    // --- lockAndFind ---

    @Test
    @DisplayName("MEM-019 the token is found without locking, then the user row and the membership row are locked, and both are read again")
    void lockAndFind_findsThenLocksTheUserThenTheMembership_thenReads() {
        givenTheToken();
        when(lockUserRepository.lock(userId)).thenReturn(true);
        when(lockMembershipRepository.lock(userId, communityId)).thenReturn(true);
        givenTheUser(user);
        givenTheMembership(membership(true, null));

        InvitationTokenResolver.UsableInvitation found = resolver.lockAndFind(TOKEN, attempt);

        assertThat(found.token()).isEqualTo(token);
        InOrder order = inOrder(getOneTimeTokenService, lockUserRepository, lockMembershipRepository,
                getUserRepository, getMembershipsRepository);
        order.verify(getOneTimeTokenService).findMembership(TOKEN, OneTimeTokenPurpose.INVITATION);
        order.verify(lockUserRepository).lock(userId);
        order.verify(lockMembershipRepository).lock(userId, communityId);
        order.verify(getUserRepository).findById(userId);
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(user.getId(), communityId);
        verify(attempt, never()).failed();
    }

    @Test
    @DisplayName("MEM-019 when the user row cannot be locked, the token is refused and the membership row is not locked")
    void lockAndFind_refusesWhenTheUserCannotBeLocked() {
        givenTheToken();
        when(lockUserRepository.lock(userId)).thenReturn(false);

        assertRefused(() -> resolver.lockAndFind(TOKEN, attempt));

        verifyNoInteractions(lockMembershipRepository);
    }

    @Test
    @DisplayName("MEM-019 when the membership row cannot be locked, the token is refused")
    void lockAndFind_refusesWhenTheMembershipCannotBeLocked() {
        givenTheToken();
        when(lockUserRepository.lock(userId)).thenReturn(true);
        when(lockMembershipRepository.lock(userId, communityId)).thenReturn(false);

        assertRefused(() -> resolver.lockAndFind(TOKEN, attempt));
    }

    @Test
    @DisplayName("MEM-015 a membership accepted while the locks were awaited is refused")
    void lockAndFind_refusesAMembershipAcceptedMeanwhile() {
        givenTheToken();
        when(lockUserRepository.lock(userId)).thenReturn(true);
        when(lockMembershipRepository.lock(userId, communityId)).thenReturn(true);
        givenTheUser(user);
        givenTheMembership(membership(true, Instant.parse("2026-01-01T00:00:00Z")));

        assertRefused(() -> resolver.lockAndFind(TOKEN, attempt));
    }

    @Test
    @DisplayName("MEM-015 a token the store does not find is refused before anything is locked")
    void lockAndFind_refusesATokenTheStoreDoesNotFind_beforeLocking() {
        when(getOneTimeTokenService.findMembership(TOKEN, OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.lockAndFind(TOKEN, attempt))
                .isInstanceOf(InvitationTokenInvalidException.class);

        verify(attempt).failed();
        verifyNoInteractions(lockUserRepository, lockMembershipRepository);
    }

    // --- invalid ---

    @Test
    @DisplayName("MEM-015 a refusal counts once against the client address, and never carries the token")
    void invalid_countsTheAttempt_andCarriesNoToken() {
        InvitationTokenInvalidException refusal = resolver.invalid(attempt);

        verify(attempt).failed();
        assertThat(refusal.getMessage()).doesNotContain(TOKEN);
    }

    // --- Helpers ---

    private void givenTheToken() {
        when(getOneTimeTokenService.findMembership(TOKEN, OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.of(token));
    }

    private void givenTheUser(User found) {
        when(getUserRepository.findById(userId)).thenReturn(Optional.of(found));
    }

    private void givenTheMembership(CommunityMembership membership) {
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.of(membership));
    }

    private CommunityMembership membership(boolean enabled, Instant acceptedAt) {
        return new CommunityMembership.Builder()
                .withId(token.membershipId())
                .withUser(user)
                .withEnabled(enabled)
                .withFullName("Lucía Pérez Gómez")
                .withAcceptedAt(acceptedAt)
                .build();
    }

    private void assertRefused(Runnable resolution) {
        assertThatThrownBy(resolution::run).isInstanceOf(InvitationTokenInvalidException.class);
        verify(attempt).failed();
    }
}
