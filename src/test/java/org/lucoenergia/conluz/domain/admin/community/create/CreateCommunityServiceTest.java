package org.lucoenergia.conluz.domain.admin.community.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCommunityServiceTest {

    @Mock
    private CreateCommunityRepository repository;

    private CreateCommunityService service() {
        return new org.lucoenergia.conluz.infrastructure.admin.community.create.CreateCommunityServiceImpl(repository);
    }

    @Test
    void create_initializesUuidBeforePersisting() {
        Community community = CommunityMother.random().build();
        when(repository.create(community)).thenReturn(community);

        service().create(community);

        ArgumentCaptor<Community> captor = ArgumentCaptor.forClass(Community.class);
        verify(repository).create(captor.capture());
        assertNotNull(captor.getValue().getId());
    }

    @Test
    void create_returnsRepositoryResult() {
        Community community = CommunityMother.random().build();
        Community saved = CommunityMother.random().build();
        when(repository.create(community)).thenReturn(saved);

        Community result = service().create(community);

        assertNotNull(result);
        verify(repository).create(community);
    }
}
