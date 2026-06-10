package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.access.SharingAgreementAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharingAgreementAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private SharingAgreementAccessGuard guard() {
        return new SharingAgreementAccessGuardImpl(helper, getSharingAgreementRepository);
    }

    @Test
    void canManageSharingAgreement_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageSharingAgreement(UUID.randomUUID()));
    }

    @Test
    void canManageSharingAgreement_returnsFalse_whenAgreementNotFound() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementRepository.findById(SharingAgreementId.of(agreementId))).thenReturn(Optional.empty());

        assertFalse(guard().canManageSharingAgreement(agreementId));
    }

    @Test
    void canManageSharingAgreement_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);

        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, null, null, communityId);
        when(getSharingAgreementRepository.findById(SharingAgreementId.of(agreementId))).thenReturn(Optional.of(agreement));

        assertTrue(guard().canManageSharingAgreement(agreementId));
    }

    @Test
    void canManageSharingAgreement_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        UUID agreementId = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement(agreementId, null, null, community.getId());
        when(getSharingAgreementRepository.findById(SharingAgreementId.of(agreementId))).thenReturn(Optional.of(agreement));

        assertFalse(guard().canManageSharingAgreement(agreementId));
    }
}
