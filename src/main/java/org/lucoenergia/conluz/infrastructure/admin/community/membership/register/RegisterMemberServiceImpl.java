package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RegisterMemberServiceImpl implements RegisterMemberService {

    private final MembershipRegistration registration;

    public RegisterMemberServiceImpl(MembershipRegistration registration) {
        this.registration = registration;
    }

    @Override
    public RegisteredMember register(UUID communityId, UserId registeredBy, UserPersonalId personalId,
                                     MemberRecord record) {
        return registration.register(communityId, registeredBy, personalId, record, CommunityRole.COMMUNITY_MEMBER);
    }
}
