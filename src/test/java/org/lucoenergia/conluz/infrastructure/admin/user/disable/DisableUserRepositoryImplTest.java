package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class DisableUserRepositoryImplTest extends BaseIntegrationTest {

    private static final Instant FIRST_DISABLE = Instant.parse("2026-10-04T10:11:12.345Z");
    private static final Instant SECOND_DISABLE = Instant.parse("2026-10-05T08:09:10.111Z");

    @Autowired
    private DisableUserRepository repository;
    @Autowired
    private EnableUserRepository enableUserRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void disable_disablesTheUser_andRecordsTheInstant() {
        UserId id = createEnabledUser();

        repository.disable(id, FIRST_DISABLE);

        User stored = reload(id);
        assertFalse(stored.isEnabled());
        assertEquals(FIRST_DISABLE, stored.getDisabledAt());
    }

    @Test
    void disablingAgain_replacesTheRecordedInstant() {
        UserId id = createEnabledUser();
        repository.disable(id, FIRST_DISABLE);
        enableUserRepository.enable(id);

        repository.disable(id, SECOND_DISABLE);

        assertEquals(SECOND_DISABLE, reload(id).getDisabledAt());
    }

    @Test
    void enablingTheUser_keepsTheRecordedInstant() {
        UserId id = createEnabledUser();
        repository.disable(id, FIRST_DISABLE);

        enableUserRepository.enable(id);

        User stored = reload(id);
        assertTrue(stored.isEnabled());
        assertEquals(FIRST_DISABLE, stored.getDisabledAt());
    }

    @Test
    void aNewUser_hasNoDisableRecorded() {
        UserId id = createEnabledUser();

        assertNull(reload(id).getDisabledAt());
    }

    @Test
    void disable_reportsAnUnknownUserAsNotFound() {
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThrows(UserNotFoundException.class, () -> repository.disable(unknown, FIRST_DISABLE));
    }

    private UserId createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return UserId.of(user.getId());
    }

    private User reload(UserId id) {
        return getUserRepository.findById(id).orElseThrow();
    }
}
