package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The narrow write behind {@code PUT /users/current/password}: exactly the password, the flag it satisfies and
 * the change instant, and nothing else.
 */
@Transactional
class ChangePasswordRepositoryDatabaseTest extends BaseIntegrationTest {

    private static final Set<String> WRITABLE_COLUMNS = Set.of("password", "mustChangePassword", "passwordChangedAt");
    private static final String NEW_HASH = "$2a$10$aNewHashWrittenByThePasswordChangeXXXXXXXXXXXXXXXXXXXXX";
    private static final Instant CHANGED_AT = Instant.parse("2026-10-04T10:11:12.345Z");

    @Autowired
    private ChangePasswordRepository repository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void onlyThePasswordColumnsChange() {
        UserEntity seeded = seedFlaggedUser();
        Map<String, String> before = snapshot(seeded);

        repository.changePassword(UserId.of(seeded.getId()), NEW_HASH, CHANGED_AT);

        Map<String, String> after = snapshot(reload(seeded.getId()));
        Set<String> changed = before.keySet().stream()
                .filter(column -> !before.get(column).equals(after.get(column)))
                .collect(Collectors.toSet());
        assertEquals(WRITABLE_COLUMNS, changed,
                () -> "the password write must touch exactly " + WRITABLE_COLUMNS + ", but changed " + changed);
    }

    @Test
    void storesTheHash_clearsTheFlag_andRecordsTheChange() {
        UserEntity seeded = seedFlaggedUser();

        repository.changePassword(UserId.of(seeded.getId()), NEW_HASH, CHANGED_AT);

        UserEntity stored = reload(seeded.getId());
        assertEquals(NEW_HASH, stored.getPassword());
        assertFalse(stored.mustChangePassword());
        assertEquals(CHANGED_AT, stored.getPasswordChangedAt());
    }

    @Test
    void reportsAnUnknownUserAsNotFound() {
        UserId unknown = UserId.of(UUID.randomUUID());

        assertThrows(UserNotFoundException.class, () -> repository.changePassword(unknown, NEW_HASH, CHANGED_AT));
    }

    private UserEntity seedFlaggedUser() {
        User user = UserMother.randomUser();
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setPersonalId(user.getPersonalId());
        entity.setNumber(4242);
        entity.setPassword("$2a$10$anOldHashThatThePasswordChangeReplacesXXXXXXXXXXXXXXXXX");
        entity.setFullName("Nombre Original");
        entity.setAddress("Dirección Original");
        entity.setEmail("original@email.com");
        entity.setPhoneNumber("+34600999888");
        entity.setEnabled(false);
        entity.setPlatformAdmin(true);
        entity.setMustChangePassword(true);
        entity.setPasswordChangedAt(Instant.parse("2026-01-02T03:04:05.678Z"));
        return userRepository.saveAndFlush(entity);
    }

    private UserEntity reload(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    private Map<String, String> snapshot(UserEntity entity) {
        Map<String, String> values = new TreeMap<>();
        for (Field field : UserEntity.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                values.put(field.getName(),
                        value instanceof Collection<?> collection ? "size=" + collection.size()
                                : String.valueOf(value));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot read " + field.getName(), e);
            }
        }
        return values;
    }
}
