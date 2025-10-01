package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetUserServiceImplTest {

    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final GetUserServiceImpl getUserService = new GetUserServiceImpl(getUserRepository);

    @Test
    void findById_shouldReturnUserWhenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User expectedUser = UserMother.randomUserWithId(userId);
        UserId userIdValue = UserId.of(userId);

        when(getUserRepository.findById(userIdValue)).thenReturn(Optional.of(expectedUser));

        // When
        User result = getUserService.findById(userIdValue);

        // Then
        assertNotNull(result);
        assertEquals(expectedUser.getId(), result.getId());
        assertEquals(expectedUser.getPersonalId(), result.getPersonalId());
        assertEquals(expectedUser.getFullName(), result.getFullName());
        verify(getUserRepository).findById(userIdValue);
    }

    @Test
    void findById_shouldThrowUserNotFoundExceptionWhenUserDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);

        when(getUserRepository.findById(userIdValue)).thenReturn(Optional.empty());

        // When & Then
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            getUserService.findById(userIdValue);
        });

        assertEquals(userId.toString(), exception.getId());
        verify(getUserRepository).findById(userIdValue);
    }
}
