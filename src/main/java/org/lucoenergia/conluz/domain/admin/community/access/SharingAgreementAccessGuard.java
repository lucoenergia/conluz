package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface SharingAgreementAccessGuard {

    boolean canManageSharingAgreement(UUID agreementId);
}
