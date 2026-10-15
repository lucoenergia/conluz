package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.admins.EnableCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
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
class EnableCommunityAdminServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());

    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private SetMembershipEnabledRepository setMembershipEnabledRepository;

    private EnableCommunityAdminService service() {
        return new EnableCommunityAdminServiceImpl(getCommunityRepository, lockUserRepository,
                lockMembershipRepository, setMembershipEnabledRepository);
    }

    @Test
    @DisplayName("MEM-023 the admin membership is enabled once the user and the membership are locked, in that order")
    void theAdminMembershipIsEnabled_underTheLocks() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(true);
        when(setMembershipEnabledRepository.setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN, true))
                .thenReturn(true);

        service().enable(COMMUNITY_ID, USER_ID);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, setMembershipEnabledRepository);
        order.verify(lockUserRepository).lock(USER_ID);
        order.verify(lockMembershipRepository).lock(USER_ID, COMMUNITY_ID);
        order.verify(setMembershipEnabledRepository).setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN,
                true);
    }

    @Test
    @DisplayName("MEM-023 a membership that is not an admin's is not found")
    void aMembershipOfAnotherRole_isNotFound() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(true);
        when(setMembershipEnabledRepository.setEnabled(COMMUNITY_ID, USER_ID, CommunityRole.COMMUNITY_ADMIN, true))
                .thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().enable(COMMUNITY_ID, USER_ID));
    }

    @Test
    void aUserWithoutAMembershipThere_isNotFound_andNothingIsWritten() {
        communityExists();
        when(lockUserRepository.lock(USER_ID)).thenReturn(true);
        when(lockMembershipRepository.lock(USER_ID, COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().enable(COMMUNITY_ID, USER_ID));

        verifyNoInteractions(setMembershipEnabledRepository);
    }

    @Test
    void anUnknownCommunity_isNotFound_andNothingIsLocked() {
        when(getCommunityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().enable(COMMUNITY_ID, USER_ID));

        verifyNoInteractions(lockUserRepository, lockMembershipRepository, setMembershipEnabledRepository);
    }

    private void communityExists() {
        when(getCommunityRepository.findById(COMMUNITY_ID))
                .thenReturn(Optional.of(new Community.Builder().withId(COMMUNITY_ID).build()));
    }
}
