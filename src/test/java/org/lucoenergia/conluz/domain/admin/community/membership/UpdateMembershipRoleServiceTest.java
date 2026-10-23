package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMembershipRoleServiceTest {

    @Mock
    private UpdateMembershipRoleRepository updateMembershipRoleRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private LockCommunityRepository lockCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;

    private UpdateMembershipRoleService service() {
        return new UpdateMembershipRoleServiceImpl(updateMembershipRoleRepository, getCommunityRepository,
                getOneTimeTokenService, getMembershipsRepository, lockCommunityRepository, lockUserRepository,
                lockMembershipRepository);
    }

    @Test
    void updateRole_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        CommunityMembership expected = mock(CommunityMembership.class);

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_ADMIN)
                .build());
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(expected);

        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");
        when(getOneTimeTokenService.findValidExpiryForMembership(UserId.of(userId), community.getId(),
                OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.of(expiry));

        MembershipInRoster result = service().updateRole(community.getId(), userId,
                CommunityRole.COMMUNITY_ADMIN);

        assertSame(expected, result.membership());
        assertEquals(expiry, result.invitationExpiresAt());
        verify(updateMembershipRoleRepository).updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN);
    }

    @Test
    void updateRole_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().updateRole(communityId, UUID.randomUUID(), CommunityRole.COMMUNITY_ADMIN));

        verifyNoInteractions(updateMembershipRoleRepository, getOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-024 giving the admin role to a membership without a contact email is refused before writing")
    void updateRole_refusesAdminRole_whenTheRecordHasNoContactEmail() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_MEMBER)
                .build());

        assertThrows(MembershipWithoutContactEmailException.class,
                () -> service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN));

        verifyNoInteractions(updateMembershipRoleRepository, getOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-024 a membership that is already an admin keeps the role without a contact email")
    void updateRole_keepsAnExistingAdmin_withoutAContactEmail() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_ADMIN)
                .build());
        CommunityMembership expected = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(expected);

        assertSame(expected, service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN)
                .membership());
    }

    // The last-active-admin rule (MEM-033) now reads the membership on a demotion, so this checks that its contact
    // email is not consulted, where it used to check that the membership was not read at all.
    @Test
    @DisplayName("MEM-024 demoting to member needs no contact email, and the contact email is not consulted")
    void updateRole_toMember_doesNotReadTheRecord() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        CommunityMembership target = mock(CommunityMembership.class);
        when(target.getRole()).thenReturn(CommunityRole.COMMUNITY_MEMBER);
        locked(community.getId(), userId, target);
        CommunityMembership expected = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER))
                .thenReturn(expected);

        assertSame(expected, service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER)
                .membership());
        verify(target, never()).getContactEmail();
    }

    @Test
    @DisplayName("MEM-033 a role change locks the community, then the user, then the membership, before reading it")
    void updateRole_locksTheCommunityThenTheUserThenTheMembership() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        UserId user = UserId.of(userId);
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, admin(true));
        when(getMembershipsRepository.countActiveAdmins(community.getId())).thenReturn(2L);
        CommunityMembership demoted = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER))
                .thenReturn(demoted);

        service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER);

        InOrder order = inOrder(lockCommunityRepository, lockUserRepository, lockMembershipRepository,
                getMembershipsRepository, updateMembershipRoleRepository);
        order.verify(lockCommunityRepository).lock(community.getId());
        order.verify(lockUserRepository).lock(user);
        order.verify(lockMembershipRepository).lock(user, community.getId());
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(userId, community.getId());
        order.verify(getMembershipsRepository).countActiveAdmins(community.getId());
        order.verify(updateMembershipRoleRepository).updateRole(community.getId(), userId,
                CommunityRole.COMMUNITY_MEMBER);
    }

    @Test
    @DisplayName("MEM-033 demoting the last active admin is refused, and nothing is written")
    void updateRole_refusesToDemoteTheLastActiveAdmin() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, admin(true));
        when(getMembershipsRepository.countActiveAdmins(community.getId())).thenReturn(1L);

        assertThrows(MembershipLastActiveAdminException.class,
                () -> service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(updateMembershipRoleRepository, getOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-033 demoting an admin whose account is disabled is not refused, and the admins are not "
            + "counted")
    void updateRole_demotesAnAdminWithADisabledAccount() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        locked(community.getId(), userId, admin(false));
        CommunityMembership demoted = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER))
                .thenReturn(demoted);

        service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER);

        verify(getMembershipsRepository, never()).countActiveAdmins(any());
    }

    @Test
    @DisplayName("MEM-033 the only active admin keeping the role is answered as the last active admin")
    void updateRole_reportsTheLastActiveAdmin_afterTheChange() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        CommunityMembership onlyAdmin = admin(true);
        locked(community.getId(), userId, onlyAdmin);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(onlyAdmin);
        when(getMembershipsRepository.countActiveAdmins(community.getId())).thenReturn(1L);

        assertTrue(service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN).lastActiveAdmin());
    }

    @Test
    void updateRole_withoutMembership_throwsNotFoundAndWritesNothing() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(lockCommunityRepository.lock(community.getId())).thenReturn(true);
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), community.getId())).thenReturn(false);

        assertThrows(MembershipNotFoundException.class,
                () -> service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(getMembershipsRepository, updateMembershipRoleRepository);
    }

    private void locked(UUID communityId, UUID userId, CommunityMembership current) {
        when(lockCommunityRepository.lock(communityId)).thenReturn(true);
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId))
                .thenReturn(Optional.of(current));
    }

    private static CommunityMembership admin(boolean accountEnabled) {
        User user = UserMother.randomUser();
        user.setEnabled(accountEnabled);
        return new CommunityMembership.Builder().withUser(user).withRole(CommunityRole.COMMUNITY_ADMIN)
                .withAcceptedAt(Instant.parse("2026-01-01T00:00:00Z")).withEnabled(true)
                .withContactEmail("admin@example.org").build();
    }
}
