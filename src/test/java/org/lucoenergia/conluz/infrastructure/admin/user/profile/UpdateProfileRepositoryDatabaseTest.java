package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.profile.ContactDetails;
import org.lucoenergia.conluz.domain.admin.user.profile.UpdateProfileRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

/**
 * The narrow write behind {@code PUT /users/profile}.
 *
 * <p>The endpoint is safe because this repository can only touch three columns. That is a property
 * of the mapping, not of the guard, so it is pinned here rather than at the controller: reusing
 * {@code UpdateUserRepositoryDatabase}, or adding a line to the mapping below, must fail a test
 * even though every controller test would still pass.</p>
 */
@Transactional
class UpdateProfileRepositoryDatabaseTest extends BaseIntegrationTest {

    private static final Set<String> WRITABLE_COLUMNS = Set.of("email", "address", "phoneNumber");

    @Autowired
    private UpdateProfileRepository repository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;

    @Test
    void onlyTheContactColumnsChange() {
        // Reflective on purpose: a column added to UserEntity later is covered without anyone
        // remembering to extend this test, which is the whole point of pinning it here.
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();
        Map<String, String> before = snapshot(seeded);

        repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", "Calle Nueva 1", "+34600111222"));

        Map<String, String> after = snapshot(reload(seeded.getId()));
        Set<String> changed = before.keySet().stream()
                .filter(column -> !before.get(column).equals(after.get(column)))
                .collect(Collectors.toSet());

        assertEquals(WRITABLE_COLUMNS, changed,
                () -> "the profile write must touch exactly " + WRITABLE_COLUMNS + ", but changed " + changed);
    }

    @Test
    void theContactColumnsDoChange() {
        // The counterpart to the test above: proves it is not passing because nothing was written.
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();

        repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", "Calle Nueva 1", "+34600111222"));

        UserEntity stored = reload(seeded.getId());
        assertEquals("nuevo@email.com", stored.getEmail());
        assertEquals("Calle Nueva 1", stored.getAddress());
        assertEquals("+34600111222", stored.getPhoneNumber());
    }

    @Test
    void theColumnsThatConferIdentityOrPrivilegeSurvive() {
        // Named explicitly as well as covered reflectively, so a failure says which one moved.
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();
        String personalId = seeded.getPersonalId();
        String fullName = seeded.getFullName();
        Integer number = seeded.getNumber();
        String password = seeded.getPassword();

        repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", null, null));

        UserEntity stored = reload(seeded.getId());
        assertEquals(personalId, stored.getPersonalId());
        assertEquals(fullName, stored.getFullName());
        assertEquals(number, stored.getNumber());
        assertEquals(password, stored.getPassword(), "the password hash must not be rewritten");
        // Seeded as the opposite of the entity defaults, so an implementation that builds a fresh
        // entity instead of loading this one is caught rather than coincidentally agreeing.
        assertFalse(stored.isEnabled(), "a disabled account must not be re-enabled by a profile edit");
        assertTrue(stored.isPlatformAdmin(), "the platform-admin flag must not be rewritten");
    }

    @Test
    void theMembershipsSurvive() {
        // Memberships carry every community-scoped permission the user has.
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();
        createMembershipService.create(DEFAULT_COMMUNITY_ID, seeded.getId(), CommunityRole.COMMUNITY_ADMIN);

        repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", null, null));

        assertEquals(1, getMembershipsRepository.findByUserId(seeded.getId()).size());
        assertEquals(CommunityRole.COMMUNITY_ADMIN,
                getMembershipsRepository.findByUserId(seeded.getId()).get(0).getRole());
    }

    @Test
    void omittingAddressAndPhoneClearsThem() {
        // Documented behaviour of the endpoint: it replaces the contact block rather than patching it.
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();

        repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", null, null));

        UserEntity stored = reload(seeded.getId());
        assertNull(stored.getAddress());
        assertNull(stored.getPhoneNumber());
        assertEquals("nuevo@email.com", stored.getEmail());
    }

    @Test
    void itReturnsTheUpdatedUser() {
        UserEntity seeded = seedUserWithDistinctValuesEverywhere();

        User returned = repository.updateContactDetails(UserId.of(seeded.getId()),
                new ContactDetails("nuevo@email.com", "Calle Nueva 1", "+34600111222"));

        assertEquals(seeded.getId(), returned.getId());
        assertEquals("nuevo@email.com", returned.getEmail());
        assertEquals("Calle Nueva 1", returned.getAddress());
        assertEquals("+34600111222", returned.getPhoneNumber());
        assertEquals(seeded.getPersonalId(), returned.getPersonalId());
    }

    @Test
    void itReportsAnUnknownUserAsNotFound() {
        UserId unknown = UserId.of(UUID.randomUUID());
        ContactDetails details = new ContactDetails("nuevo@email.com", null, null);

        assertThrows(UserNotFoundException.class, () -> repository.updateContactDetails(unknown, details));
    }

    // --- helpers ---

    /**
     * A user whose every column holds a value distinguishable from the entity's defaults, so that an
     * implementation which resets a column — or builds a new entity instead of loading this one —
     * shows up as a difference rather than passing by coincidence.
     */
    private UserEntity seedUserWithDistinctValuesEverywhere() {
        User user = UserMother.randomUser();
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setPersonalId(user.getPersonalId());
        entity.setNumber(4242);
        entity.setPassword("$2a$12$aStoredHashThatMustSurviveAProfileEditXXXXXXXXXXXXXXX");
        entity.setFullName("Nombre Original");
        entity.setAddress("Dirección Original");
        entity.setEmail("original@email.com");
        entity.setPhoneNumber("+34600999888");
        entity.setEnabled(false);
        entity.setPlatformAdmin(true);
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
