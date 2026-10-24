package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.admins.DisableCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisableCommunityAdminServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final UserId PLATFORM_ADMIN = UserId.of(UUID.randomUUID());

    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private SetMembershipEnabledRepository setMembershipEnabledRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private CommunityAdminChangeNotifier notifier;

    private DisableCommunityAdminService service() {
        return new DisableCommunityAdminServiceImpl(getCommunityRepository, lockUserRepository,
                lockMembershipRepository, setMembershipEnabledRepository,
                getMembershipsRepository, notifier);
    }

    @Test
    @DisplayName("MEM-023 the admin membership is disabled once the user and the membership are locked, in that order")
    void theAdminMembershipIsDisabled_underTheLocks() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(true);
        membershipExists();
        when(setMembershipEnabledRepository.setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN, false))
                .thenReturn(true);

        service().disable(COMMUNITY_ID, USER_ID, PLATFORM_ADMIN);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, setMembershipEnabledRepository);
        order.verify(lockUserRepository).lock(USER_ID);
        order.verify(lockMembershipRepository).lock(USER_ID, COMMUNITY_ID);
        order.verify(setMembershipEnabledRepository).setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN,
                false);
    }

    @Test
    @DisplayName("MEM-023 a membership that is not an admin's is not found")
    void aMembershipOfAnotherRole_isNotFound() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(true);
        membershipExists();
        when(setMembershipEnabledRepository.setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN, false))
                .thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().disable(COMMUNITY_ID, USER_ID, PLATFORM_ADMIN));
    }

    @Test
    void aUserWithoutAMembershipThere_isNotFound_andNothingIsWritten() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().disable(COMMUNITY_ID, USER_ID, PLATFORM_ADMIN));

        verifyNoInteractions(setMembershipEnabledRepository);
    }

    @Test
    void anUnknownCommunity_isNotFound_andNothingIsLocked() {
        when(getCommunityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().disable(COMMUNITY_ID, USER_ID, PLATFORM_ADMIN));

        verifyNoInteractions(lockUserRepository, lockMembershipRepository, setMembershipEnabledRepository);
    }

    private void membershipExists() {
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID.getId(), COMMUNITY_ID))
                .thenReturn(Optional.of(new CommunityMembership.Builder().build()));
    }

    private void communityExists() {
        when(getCommunityRepository.findById(COMMUNITY_ID))
                .thenReturn(Optional.of(new Community.Builder().withId(COMMUNITY_ID).build()));
    }
}
