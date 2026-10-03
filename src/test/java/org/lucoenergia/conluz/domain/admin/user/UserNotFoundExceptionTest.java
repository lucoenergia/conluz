package org.lucoenergia.conluz.domain.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserNotFoundExceptionTest {

    @Test
    void testIdIsEmptyWhenNoArgsConstructorIsCalled() {
        // arrange
        UserNotFoundException ex = new UserNotFoundException();

        // act
        String id = ex.getId();

        // assert
        assertEquals("", id, "ID should be empty when no-args constructor is called");
    }

    @Test
    void testIdIsUserIdWhenUserIdConstructorIsCalled() {
        // arrange
        UserId userId = UserId.of(UUID.randomUUID());
        UserNotFoundException ex = new UserNotFoundException(userId);

        // act
        String id = ex.getId();

        // assert
        assertEquals(userId.getId().toString(), id, "ID should match the UserId when UserId constructor is called");
    }

    @Test
    void notFoundByPersonalIdCarriesNoIdentifier() {
        UserNotFoundException ex = UserNotFoundException.forPersonalId();

        assertTrue(ex.isLookedUpByPersonalId());
        assertEquals("", ex.getId());
        assertTrue(ex.getUserId().isEmpty());
    }

    @Test
    void notFoundByUserIdIsNotLookedUpByPersonalId() {
        assertFalse(new UserNotFoundException(UserId.of(UUID.randomUUID())).isLookedUpByPersonalId());
    }
}
