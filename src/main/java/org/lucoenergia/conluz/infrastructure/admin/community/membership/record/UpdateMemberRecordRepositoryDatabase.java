package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class UpdateMemberRecordRepositoryDatabase implements UpdateMemberRecordRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;

    public UpdateMemberRecordRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public boolean update(UUID communityId, UserId userId, MemberRecord record) {
        Optional<CommunityMembershipEntity> membership = membershipJpaRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId);
        membership.ifPresent(entity -> {
            entity.setFullName(record.fullName());
            entity.setMemberNumber(record.memberNumber());
            entity.setContactEmail(record.contactEmail());
            entity.setAddress(record.address());
            entity.setPhoneNumber(record.phoneNumber());
            membershipJpaRepository.saveAndFlush(entity);
        });
        return membership.isPresent();
    }
}
