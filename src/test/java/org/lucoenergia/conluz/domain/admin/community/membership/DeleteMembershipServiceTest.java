package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DeleteMembershipServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMembershipServiceTest {

    @Mock
    private DeleteMembershipRepository deleteMembershipRepository;

    private DeleteMembershipService service() {
        return new DeleteMembershipServiceImpl(deleteMembershipRepository);
    }

    @Test
    void delete_delegatesToRepository() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service().delete(communityId, userId);

        verify(deleteMembershipRepository).delete(communityId, userId);
    }
}
