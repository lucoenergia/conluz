package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DeleteMembershipServiceImpl implements DeleteMembershipService {

    private final DeleteMembershipRepository deleteMembershipRepository;

    public DeleteMembershipServiceImpl(DeleteMembershipRepository deleteMembershipRepository) {
        this.deleteMembershipRepository = deleteMembershipRepository;
    }

    @Override
    public void delete(UUID communityId, UUID userId) {
        deleteMembershipRepository.delete(communityId, userId);
    }
}
