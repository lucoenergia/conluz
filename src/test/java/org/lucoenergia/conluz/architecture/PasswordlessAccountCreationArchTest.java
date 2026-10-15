package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.MembershipRegistration;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreatePasswordlessUserRepositoryDatabase;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that an account without a password is created only by the registration shared by
 * registering a member and inviting a community admin.
 *
 * <p>Every other creation path hashes a password through the password policy, which refuses a missing one. The
 * passwordless port takes no password at all, so a second caller would be a second way to store an account that
 * can get its first password without accepting an invitation.</p>
 */
public class PasswordlessAccountCreationArchTest extends BaseArchTest {

    @Test
    @DisplayName("ACC-006 only the membership registration creates an account without a password")
    void onlyMembershipRegistrationCreatesPasswordlessAccounts() {
        noClasses()
                .that().doNotBelongToAnyOf(MembershipRegistration.class,
                        CreatePasswordlessUserRepositoryDatabase.class)
                .should().dependOnClassesThat().areAssignableTo(CreatePasswordlessUserRepository.class)
                .because("a passwordless account gets its first password only by accepting an invitation "
                        + "(ADR-0003 §7), so only registering a member or inviting a community admin may "
                        + "create one")
                .check(IMPORTED_CLASSES);
    }
}
