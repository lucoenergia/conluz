package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordService;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class UpdateMemberRecordServiceImpl implements UpdateMemberRecordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateMemberRecordServiceImpl.class);

    private final GetCommunityRepository getCommunityRepository;
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final UpdateMemberRecordRepository updateMemberRecordRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final RevokeOneTimeTokenService revokeOneTimeTokenService;

    public UpdateMemberRecordServiceImpl(GetCommunityRepository getCommunityRepository,
                                         LockUserRepository lockUserRepository,
                                         LockMembershipRepository lockMembershipRepository,
                                         GetMembershipsRepository getMembershipsRepository,
                                         UpdateMemberRecordRepository updateMemberRecordRepository,
                                         GetOneTimeTokenService getOneTimeTokenService,
                                         RevokeOneTimeTokenService revokeOneTimeTokenService) {
        this.getCommunityRepository = getCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.updateMemberRecordRepository = updateMemberRecordRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.revokeOneTimeTokenService = revokeOneTimeTokenService;
    }

    @Override
    public MembershipWithInvitation update(UUID communityId, UserId userId, MemberRecord record) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // Lock order: user row, then membership row, then token rows, as sending and accepting an invitation take
        // them, so a send reads either the old contact email or the new one, and the role checked below cannot
        // change before the record is written
        if (!lockUserRepository.lock(userId) || !lockMembershipRepository.lock(userId, communityId)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        CommunityMembership current = getMembershipsRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        // A community admin is contacted through the contact email of their member record (ADR-0003 §8)
        if (current.getRole() == CommunityRole.COMMUNITY_ADMIN && !record.hasContactEmail()) {
            throw new MembershipWithoutContactEmailException(communityId, userId.getId());
        }

        if (!updateMemberRecordRepository.update(communityId, userId, record)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        // A link sent to the old address of a pending membership stops working: the new address is reached by a send
        // or a resend (#482)
        boolean invitationRevoked = current.isPending()
                && !sameMailbox(current.getContactEmail(), record.contactEmail())
                && revokeOneTimeTokenService.revokeForMembership(userId, communityId, OneTimeTokenPurpose.INVITATION);
        CommunityMembership updated = getMembershipsRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));

        LOGGER.info("Member record updated: userId={}, communityId={}, invitationRevoked={}", userId.getId(), communityId,
                invitationRevoked);
        return new MembershipWithInvitation(updated, getOneTimeTokenService
                .findValidExpiryForMembership(userId, communityId, OneTimeTokenPurpose.INVITATION)
                .orElse(null));
    }

    /**
     * Whether two contact emails reach the same mailbox in practice: equal once trimmed and lower-cased, none and
     * blank being equal. Used only to decide whether the address changed; the email is stored as it is sent.
     */
    private static boolean sameMailbox(String before, String after) {
        return comparable(before).equals(comparable(after));
    }

    private static String comparable(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
