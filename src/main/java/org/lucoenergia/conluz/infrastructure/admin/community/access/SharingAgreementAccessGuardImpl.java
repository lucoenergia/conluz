package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.SharingAgreementAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Optional;
import java.util.UUID;

class SharingAgreementAccessGuardImpl implements SharingAgreementAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public SharingAgreementAccessGuardImpl(CommunityAccessGuardHelper helper,
                                           GetSharingAgreementRepository getSharingAgreementRepository) {
        this.helper = helper;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
    }

    @Override
    public boolean canManageSharingAgreement(UUID agreementId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        Optional<SharingAgreement> agreement = getSharingAgreementRepository.findById(
                SharingAgreementId.of(agreementId));
        if (agreement.isEmpty()) {
            return false;
        }
        UUID communityId = agreement.get().getCommunityId();
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }
}
