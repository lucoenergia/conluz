package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Transactional
class CreatePasswordlessUserRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private CreatePasswordlessUserRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ACC-006 a new personal id gets an account without a password, a number or a password to change")
    void aNewPersonalId_getsAnAccountWithoutAPasswordOrANumber() {
        String personalId = UserMother.randomPersonalId();

        UserId created = repository.createIfAbsent(UserPersonalId.of(" " + personalId.toLowerCase() + " "),
                "Lucía Pérez", "contact@example.org");

        Map<String, Object> row = row(created);
        assertEquals(personalId, row.get("personal_id"));
        assertNull(row.get("password"));
        assertNull(row.get("number"));
        assertEquals(false, row.get("must_change_password"));
        assertEquals("Lucía Pérez", row.get("full_name"));
        assertEquals("contact@example.org", row.get("email"));
        assertEquals(true, row.get("enabled"));
        assertEquals(false, row.get("is_platform_admin"));
        assertNull(row.get("address"));
        assertNull(row.get("phone_number"));
        assertNull(row.get("password_changed_at"));
        assertNull(row.get("disabled_at"));
    }

    @Test
    @DisplayName("ACC-006 an empty or blank email is stored as no email")
    void anEmptyOrBlankEmail_isStoredAsNoEmail() {
        assertNull(row(repository.createIfAbsent(UserPersonalId.of(UserMother.randomPersonalId()), "Lucía", ""))
                .get("email"));
        assertNull(row(repository.createIfAbsent(UserPersonalId.of(UserMother.randomPersonalId()), "Lucía", " \t"))
                .get("email"));
        assertNull(row(repository.createIfAbsent(UserPersonalId.of(UserMother.randomPersonalId()), "Lucía", null))
                .get("email"));
    }

    @Test
    @DisplayName("ACC-006 an existing account is returned and never written, whether or not it has a password")
    void anExistingAccount_isReturned_andLeftUnchanged() {
        User existing = UserMother.randomUser();
        existing.enable();
        createUserRepository.create(existing);
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", existing.getId());

        UserId found = repository.createIfAbsent(UserPersonalId.of(existing.getPersonalId().toLowerCase()),
                "Someone Else", "someone-else@example.org");

        assertEquals(existing.getId(), found.getId());
        assertEquals(before, row(found));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE personal_id = ?",
                Integer.class, existing.getPersonalId()));

        UserId passwordless = repository.createIfAbsent(UserPersonalId.of(UserMother.randomPersonalId()), "Lucía",
                "first@example.org");
        Map<String, Object> passwordlessBefore = row(passwordless);
        String personalId = (String) passwordlessBefore.get("personal_id");

        assertEquals(passwordless, repository.createIfAbsent(UserPersonalId.of(personalId), "Other",
                "second@example.org"));
        assertEquals(passwordlessBefore, row(passwordless));
    }

    private Map<String, Object> row(UserId id) {
        return jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", id.getId());
    }
}
