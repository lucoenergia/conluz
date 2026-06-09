package org.lucoenergia.conluz.domain.admin.community.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.community.update.UpdateCommunityServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCommunityServiceTest {

    @Mock
    private UpdateCommunityRepository updateCommunityRepository;

    private UpdateCommunityService service() {
        return new UpdateCommunityServiceImpl(updateCommunityRepository);
    }

    @Test
    void update_delegatesToRepositoryAndReturnsResult() {
        UUID id = UUID.randomUUID();
        Community updated = CommunityMother.random().build();
        Community expected = CommunityMother.random().build();

        when(updateCommunityRepository.update(id, updated)).thenReturn(expected);

        Community result = service().update(id, updated);

        assertEquals(expected, result);
        verify(updateCommunityRepository).update(id, updated);
    }

    @Test
    void update_propagatesCommunityNotFoundException() {
        UUID id = UUID.randomUUID();
        Community updated = CommunityMother.random().build();

        when(updateCommunityRepository.update(id, updated)).thenThrow(new CommunityNotFoundException(id));

        assertThrows(CommunityNotFoundException.class, () -> service().update(id, updated));
    }
}
