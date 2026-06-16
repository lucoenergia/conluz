package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.SharingAgreementAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.user.User;

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
        SharingAgreement agreement = getSharingAgreementRepository.findById(
                SharingAgreementId.of(agreementId)).orElse(null);
        // Sharing agreements are only visible to admins of their community, so a caller who cannot
        // manage it cannot see it either: deny with a 404 to avoid leaking the agreement's existence.
        if (agreement == null || !helper.hasCommunityAdminRoleIn(user, agreement.getCommunityId())) {
            throw new SharingAgreementNotFoundException(SharingAgreementId.of(agreementId));
        }
        return true;
    }
}
