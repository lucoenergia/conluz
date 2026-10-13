package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RegisterMemberServiceImpl implements RegisterMemberService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterMemberServiceImpl.class);

    private final GetCommunityRepository getCommunityRepository;
    private final GetUserRepository getUserRepository;
    private final CreatePasswordlessUserRepository createPasswordlessUserRepository;
    private final LockUserRepository lockUserRepository;
    private final CreateMembershipRepository createMembershipRepository;
    private final SendInvitationService sendInvitationService;

    public RegisterMemberServiceImpl(GetCommunityRepository getCommunityRepository,
                                     GetUserRepository getUserRepository,
                                     CreatePasswordlessUserRepository createPasswordlessUserRepository,
                                     LockUserRepository lockUserRepository,
                                     CreateMembershipRepository createMembershipRepository,
                                     SendInvitationService sendInvitationService) {
        this.getCommunityRepository = getCommunityRepository;
        this.getUserRepository = getUserRepository;
        this.createPasswordlessUserRepository = createPasswordlessUserRepository;
        this.lockUserRepository = lockUserRepository;
        this.createMembershipRepository = createMembershipRepository;
        this.sendInvitationService = sendInvitationService;
    }

    @Override
    public RegisteredMember register(UUID communityId, UserId registeredBy, UserPersonalId personalId,
                                     MemberRecord record) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        String normalized = UserPersonalId.normalize(personalId.getPersonalId());

        // An existing account is found silently and never written. A missing one is created without a password;
        // if a concurrent registration creates it first, that account is used instead
        UserId userId = getUserRepository.findByPersonalId(UserPersonalId.of(normalized))
                .map(User::getId)
                .map(UserId::of)
                .orElseGet(() -> createPasswordlessUserRepository.createIfAbsent(UserPersonalId.of(normalized),
                        record.fullName(), record.contactEmail()));

        // Lock order: user row, then membership row, then token rows. Holding the user row also makes two
        // registrations of the same person in this community run one after the other, so the second one finds
        // the first one's membership
        lockUserRepository.lock(userId);
        CommunityMembership membership = createMembershipRepository.createPending(communityId, userId, record,
                registeredBy);

        Optional<CommunityMembership> invited = record.hasContactEmail()
                ? sendInvitationService.send(membership)
                : Optional.empty();

        LOGGER.info("Member registered: personalId={}, communityId={}, invitationSent={}",
                UserPersonalId.mask(normalized), communityId, invited.isPresent());

        return new RegisteredMember(normalized, invited.orElse(membership));
    }
}
