package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleServiceImpl;
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

    private UpdateMembershipRoleService service() {
        return new UpdateMembershipRoleServiceImpl(updateMembershipRoleRepository, getCommunityRepository,
                getOneTimeTokenService, getMembershipsRepository);
    }

    @Test
    void updateRole_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        CommunityMembership expected = mock(CommunityMembership.class);

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(expected);

        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");
        when(getOneTimeTokenService.findValidExpiryForMembership(UserId.of(userId), community.getId(),
                OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.of(expiry));

        MembershipWithInvitation result = service().updateRole(community.getId(), userId,
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
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, community.getId()))
                .thenReturn(Optional.of(new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_MEMBER)
                        .build()));

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
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, community.getId()))
                .thenReturn(Optional.of(new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_ADMIN)
                        .build()));
        CommunityMembership expected = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(expected);

        assertSame(expected, service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN)
                .membership());
    }

    @Test
    @DisplayName("MEM-024 demoting to member needs no contact email, and the record is not read")
    void updateRole_toMember_doesNotReadTheRecord() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        CommunityMembership expected = mock(CommunityMembership.class);
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER))
                .thenReturn(expected);

        assertSame(expected, service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER)
                .membership());
        verifyNoInteractions(getMembershipsRepository);
    }
}
