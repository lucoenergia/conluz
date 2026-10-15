package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The registration shared by registering a member and inviting a community admin (ADR-0003 §3): finds the account
 * with the personal id, or creates one without a password, and creates a pending membership with the member record
 * and the given role.
 *
 * <p>The outcome is the same whether or not the account existed, and nothing about the account is written, returned
 * or logged. It runs inside the caller's transaction, so a refusal leaves nothing behind.</p>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class MembershipRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MembershipRegistration.class);

    private final GetCommunityRepository getCommunityRepository;
    private final GetUserRepository getUserRepository;
    private final CreatePasswordlessUserRepository createPasswordlessUserRepository;
    private final LockUserRepository lockUserRepository;
    private final CreateMembershipRepository createMembershipRepository;

    public MembershipRegistration(GetCommunityRepository getCommunityRepository,
                                  GetUserRepository getUserRepository,
                                  CreatePasswordlessUserRepository createPasswordlessUserRepository,
                                  LockUserRepository lockUserRepository,
                                  CreateMembershipRepository createMembershipRepository) {
        this.getCommunityRepository = getCommunityRepository;
        this.getUserRepository = getUserRepository;
        this.createPasswordlessUserRepository = createPasswordlessUserRepository;
        this.lockUserRepository = lockUserRepository;
        this.createMembershipRepository = createMembershipRepository;
    }

    /**
     * @throws CommunityNotFoundException if the community does not exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the person already
     *         has a membership in the community, whatever its state
     */
    public RegisteredMember register(UUID communityId, UserId registeredBy, UserPersonalId personalId,
                                     MemberRecord record, CommunityRole role) {
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
        CommunityMembership membership = createMembershipRepository.createPending(communityId, userId, record, role,
                registeredBy);

        LOGGER.info("Member registered: personalId={}, communityId={}", UserPersonalId.mask(normalized),
                communityId);

        return new RegisteredMember(normalized, membership);
    }
}
