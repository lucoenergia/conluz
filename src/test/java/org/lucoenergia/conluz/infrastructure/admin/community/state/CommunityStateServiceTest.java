package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityStateServiceTest {

    @Mock
    private CommunityJpaRepository repository;

    private CommunityStateService service() {
        return new CommunityStateService(repository);
    }

    @Test
    void enable_setsEnabledTrueAndSaves() {
        UUID id = UUID.randomUUID();
        CommunityEntity entity = new CommunityEntity.Builder().withId(id).withEnabled(false).build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service().enable(id);

        assertTrue(entity.isEnabled());
        verify(repository).save(entity);
    }

    @Test
    void enable_throwsCommunityNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().enable(id));
    }

    @Test
    void disable_setsEnabledFalseAndSaves() {
        UUID id = UUID.randomUUID();
        CommunityEntity entity = new CommunityEntity.Builder().withId(id).withEnabled(true).build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service().disable(id);

        assertFalse(entity.isEnabled());
        verify(repository).save(entity);
    }

    @Test
    void disable_throwsCommunityNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().disable(id));
    }
}
