package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.state.CommunityStateRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityStateServiceTest {

    @Mock
    private CommunityStateRepository repository;

    private CommunityStateService service() {
        return new CommunityStateService(repository);
    }

    @Test
    void enable_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service().enable(id);

        verify(repository).enable(id);
    }

    @Test
    void enable_propagatesCommunityNotFoundException() {
        UUID id = UUID.randomUUID();
        doThrow(new CommunityNotFoundException(id)).when(repository).enable(id);

        assertThrows(CommunityNotFoundException.class, () -> service().enable(id));
    }

    @Test
    void disable_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service().disable(id);

        verify(repository).disable(id);
    }

    @Test
    void disable_propagatesCommunityNotFoundException() {
        UUID id = UUID.randomUUID();
        doThrow(new CommunityNotFoundException(id)).when(repository).disable(id);

        assertThrows(CommunityNotFoundException.class, () -> service().disable(id));
    }
}
