package org.lucoenergia.conluz.domain.admin.user.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    @Mock
    private CreateUserRepository repository;

    private CreateUserService service() {
        return new CreateUserServiceImpl(repository);
    }

    @Test
    void create_withTheUserOnly_doesNotFlagTheUserAsHavingToChangeThePassword() {
        User user = UserMother.randomUser();
        when(repository.create(user)).thenReturn(user);

        service().create(user);

        verify(repository).create(argThat(created -> !created.mustChangePassword()));
    }
}
