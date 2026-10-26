package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordService;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateMemberRecordServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final MemberRecord WITH_EMAIL =
            new MemberRecord("Lucía Pérez Gómez", 7, "lucia@example.org", "Calle Mayor 1", "600000000");
    private static final MemberRecord WITHOUT_EMAIL =
            new MemberRecord("Lucía Pérez Gómez", 7, null, "Calle Mayor 1", "600000000");

    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private UpdateMemberRecordRepository updateMemberRecordRepository;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;
    @Mock
    private RevokeOneTimeTokenService revokeOneTimeTokenService;

    private UpdateMemberRecordService service() {
        return new UpdateMemberRecordServiceImpl(getCommunityRepository, lockUserRepository, lockMembershipRepository,
                getMembershipsRepository, updateMemberRecordRepository, getOneTimeTokenService,
                revokeOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-028 the record is written once the user and the membership are locked, in that order")
    void theRecordIsWritten_underTheLocks() {
        Instant expiry = Instant.parse("2026-10-20T10:00:00Z");
        CommunityMembership membership = membership(CommunityRole.COMMUNITY_MEMBER);
        locked(membership);
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITH_EMAIL)).thenReturn(true);
        when(getOneTimeTokenService.findValidExpiryForMembership(USER_ID, COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.of(expiry));

        MembershipWithInvitation updated = service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, getMembershipsRepository,
                updateMemberRecordRepository);
        order.verify(lockUserRepository).lock(USER_ID);
        order.verify(lockMembershipRepository).lock(USER_ID, COMMUNITY_ID);
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(USER_ID.getId(), COMMUNITY_ID);
        order.verify(updateMemberRecordRepository).update(COMMUNITY_ID, USER_ID, WITH_EMAIL);
        assertSame(membership, updated.membership());
        assertEquals(expiry, updated.invitationExpiresAt());
    }

    @Test
    @DisplayName("MEM-028 a member's record may be left without a contact email")
    void aMembersRecord_mayHaveNoContactEmail() {
        locked(membership(CommunityRole.COMMUNITY_MEMBER));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL);

        verify(updateMemberRecordRepository).update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL);
    }

    @Test
    @DisplayName("MEM-028 an admin's record without a contact email is refused, and nothing is written")
    void anAdminsRecordWithoutAContactEmail_isRefused_andNothingIsWritten() {
        locked(membership(CommunityRole.COMMUNITY_ADMIN));

        assertThrows(MembershipWithoutContactEmailException.class,
                () -> service().update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL));

        verify(updateMemberRecordRepository, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("MEM-028 an admin's record with a contact email is written")
    void anAdminsRecordWithAContactEmail_isWritten() {
        locked(membership(CommunityRole.COMMUNITY_ADMIN));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITH_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL);

        verify(updateMemberRecordRepository).update(COMMUNITY_ID, USER_ID, WITH_EMAIL);
    }

    @Test
    @DisplayName("MEM-028 a pending membership's invitation link is revoked when its contact email changes, after the "
            + "record is written under the locks")
    void aPendingMembershipsLink_isRevoked_whenItsContactEmailChanges() {
        locked(pending("old@example.org"));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITH_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, updateMemberRecordRepository,
                revokeOneTimeTokenService);
        order.verify(lockUserRepository).lock(USER_ID);
        order.verify(lockMembershipRepository).lock(USER_ID, COMMUNITY_ID);
        order.verify(updateMemberRecordRepository).update(COMMUNITY_ID, USER_ID, WITH_EMAIL);
        order.verify(revokeOneTimeTokenService).revokeForMembership(USER_ID, COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION);
    }

    @Test
    @DisplayName("MEM-028 a pending membership's invitation link is revoked when its contact email is removed")
    void aPendingMembershipsLink_isRevoked_whenItsContactEmailIsRemoved() {
        locked(pending("lucia@example.org"));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL);

        verify(revokeOneTimeTokenService).revokeForMembership(USER_ID, COMMUNITY_ID, OneTimeTokenPurpose.INVITATION);
    }

    @Test
    @DisplayName("MEM-028 a pending membership's invitation link is kept when its contact email changes only in case "
            + "or surrounding spaces")
    void aPendingMembershipsLink_isKept_whenItsContactEmailOnlyChangesInCaseOrSpaces() {
        locked(pending(" Lucia@Example.ORG "));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITH_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL);

        verifyNoInteractions(revokeOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-028 a pending membership without a contact email that stays without one keeps its state")
    void aPendingMembershipWithoutAContactEmail_revokesNothing_whenItStaysWithoutOne() {
        locked(pending(null));
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITHOUT_EMAIL);

        verifyNoInteractions(revokeOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-028 an accepted membership's contact email change revokes nothing")
    void anAcceptedMembership_revokesNothing_whenItsContactEmailChanges() {
        locked(CommunityMembershipMother.active().withContactEmail("old@example.org").build());
        when(updateMemberRecordRepository.update(COMMUNITY_ID, USER_ID, WITH_EMAIL)).thenReturn(true);

        service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL);

        verifyNoInteractions(revokeOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-028 a user without a membership in the community is not found, and nothing is written")
    void aUserWithoutAMembershipThere_isNotFound_andNothingIsWritten() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL));

        verifyNoInteractions(updateMemberRecordRepository);
    }

    @Test
    @DisplayName("MEM-028 an unknown user is not found, and nothing is written")
    void anUnknownUser_isNotFound_andNothingIsWritten() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL));

        verifyNoInteractions(lockMembershipRepository, updateMemberRecordRepository);
    }

    @Test
    @DisplayName("MEM-028 an unknown community is not found, and nothing is locked")
    void anUnknownCommunity_isNotFound_andNothingIsLocked() {
        when(getCommunityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().update(COMMUNITY_ID, USER_ID, WITH_EMAIL));

        verifyNoInteractions(lockUserRepository, lockMembershipRepository, updateMemberRecordRepository);
    }

    private void locked(CommunityMembership membership) {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID.getId(), COMMUNITY_ID))
                .thenReturn(Optional.of(membership));
    }

    private void communityExists() {
        when(getCommunityRepository.findById(COMMUNITY_ID))
                .thenReturn(Optional.of(new Community.Builder().withId(COMMUNITY_ID).build()));
    }

    private static CommunityMembership pending(String contactEmail) {
        return new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_MEMBER)
                .withFullName("Lucía Pérez Gómez").withContactEmail(contactEmail).build();
    }

    private static CommunityMembership membership(CommunityRole role) {
        return new CommunityMembership.Builder().withRole(role).withFullName("Lucía Pérez Gómez").build();
    }
}
