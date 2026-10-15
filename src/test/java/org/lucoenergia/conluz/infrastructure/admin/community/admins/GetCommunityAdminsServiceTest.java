package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.GetCommunityAdminsService;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCommunityAdminsServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;

    private GetCommunityAdminsService service() {
        return new GetCommunityAdminsServiceImpl(getMembershipsRepository, getOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-021 only the admin memberships are read, each with the personal id of its account and the "
            + "expiry of its link")
    void onlyAdminMemberships_withTheirPersonalIdAndExpiry() {
        CommunityMembership invited = admin("11111111A");
        CommunityMembership accepted = admin("22222222B");
        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");
        when(getMembershipsRepository.findByCommunityIdAndRole(COMMUNITY_ID, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(List.of(invited, accepted));
        when(getOneTimeTokenService.findValidExpiriesInCommunity(COMMUNITY_ID, OneTimeTokenPurpose.INVITATION))
                .thenReturn(Map.of(invited.getId(), expiry));

        List<CommunityAdmin> admins = service().findByCommunityId(COMMUNITY_ID);

        assertEquals(2, admins.size());
        assertSame(invited, admins.get(0).membership());
        assertEquals("11111111A", admins.get(0).personalId());
        assertEquals(expiry, admins.get(0).invitationExpiresAt());
        assertEquals("22222222B", admins.get(1).personalId());
        assertNull(admins.get(1).invitationExpiresAt());
    }

    @Test
    void anUnknownCommunity_isNotFound() {
        when(getMembershipsRepository.findByCommunityIdAndRole(COMMUNITY_ID, CommunityRole.COMMUNITY_ADMIN))
                .thenThrow(new CommunityNotFoundException(COMMUNITY_ID));

        assertThrows(CommunityNotFoundException.class, () -> service().findByCommunityId(COMMUNITY_ID));

        verifyNoInteractions(getOneTimeTokenService);
    }

    private static CommunityMembership admin(String personalId) {
        User user = new User.Builder().id(UUID.randomUUID()).personalId(personalId).build();
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .build();
    }
}
