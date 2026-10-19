package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registers one row of an imported file, in a transaction of its own, as a member registration does.
 */
@Component
@Transactional
public class MemberImportRowStep {

    private final MembershipRegistration registration;

    public MemberImportRowStep(MembershipRegistration registration) {
        this.registration = registration;
    }

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the person already
     *         has a membership in the community, whatever its state; nothing is changed
     */
    public void register(UUID communityId, UserId importedBy, UserPersonalId personalId, MemberRecord record) {
        registration.register(communityId, importedBy, personalId, record, CommunityRole.COMMUNITY_MEMBER);
    }
}
