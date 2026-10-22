package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.accept.CommunityInvitationAcceptance;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateDefaultAdminUserServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserRepositoryImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.password.ChangePasswordRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.user.password.ChangePasswordServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.password.reset.ResetPasswordServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.ManagePlatformAdminRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation.PlatformAdminInvitationAcceptance;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing who may write an account's password, personal ID and platform-admin role (#447).
 *
 * <p>Since the legacy creation, import and update endpoints are gone, an account gets a password only from the person
 * who will use it: the first admin types it into {@code /init}, and everyone else sets it by changing it, by resetting
 * it, or by accepting an invitation. No path is left that sets a password chosen by someone else or changes a
 * personal ID, and the platform-admin role is held only by the first admin and by people who accepted a platform-admin
 * invitation (ADR-0004). These rules keep it that way: a new caller of one of these writers is a new path, and must be
 * a decision rather than an accident.</p>
 *
 * <p>The account without a password created by registration and invitations is covered by
 * {@link PasswordlessAccountCreationArchTest}.</p>
 */
public class AccountCredentialWritersArchTest extends BaseArchTest {

    @Test
    @DisplayName("ACC-004 only /init creates an account with a password")
    void onlyInitCreatesAnAccountWithAPassword() {
        noClasses()
                .that().doNotBelongToAnyOf(CreateDefaultAdminUserServiceImpl.class, CreateUserServiceImpl.class)
                .should().dependOnClassesThat().areAssignableTo(CreateUserService.class)
                .because("the first admin is the only account whose password is set at creation, by the person "
                        + "who will use it (#447)")
                .check(IMPORTED_CLASSES);
        noClasses()
                .that().doNotBelongToAnyOf(CreateUserServiceImpl.class, CreateUserRepositoryImpl.class)
                .should().dependOnClassesThat().areAssignableTo(CreateUserRepository.class)
                .because("creating an account with a password is reached only through /init's creation (#447)")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("ACC-004 a stored password is changed only by changing it, resetting it or accepting an invitation")
    void onlyThePersonSetsTheirPasswordAfterCreation() {
        noClasses()
                .that().doNotBelongToAnyOf(ChangePasswordServiceImpl.class, ResetPasswordServiceImpl.class,
                        CommunityInvitationAcceptance.class, PlatformAdminInvitationAcceptance.class,
                        ChangePasswordRepositoryDatabase.class)
                .should().dependOnClassesThat().areAssignableTo(ChangePasswordRepository.class)
                .because("only the person whose account it is chooses its password (#447)")
                .check(IMPORTED_CLASSES);
        noClasses()
                .that().doNotBelongToAnyOf(UserEntity.class, ChangePasswordRepositoryDatabase.class)
                .should().callMethodWhere(target(owner(assignableTo(UserEntity.class)))
                        .and(target(name("setPassword"))))
                .because("a password is stored only by /init's creation (through UserEntity.createNewUser) and by "
                        + "the password change, reset and invitation acceptance (#447)")
                .check(IMPORTED_CLASSES);
        noClasses()
                .that().doNotBelongToAnyOf(UserEntity.class, CreateUserRepositoryImpl.class)
                .should().callMethodWhere(target(owner(assignableTo(UserEntity.class)))
                        .and(target(name("createNewUser"))))
                .because("creating an account with a password is reached only through /init's creation (#447)")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("ACC-008 a personal ID is written only when the account is created")
    void aPersonalIdIsNeverChanged() {
        noClasses()
                .that().doNotBelongToAnyOf(UserEntity.class, CreateUserRepositoryImpl.class)
                .should().callMethodWhere(target(owner(assignableTo(UserEntity.class)))
                        .and(target(name("setPersonalId"))))
                .because("no path changes the personal ID of an existing account (#447); the account without a "
                        + "password stores its own through the passwordless registration")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("ACC-014 the platform-admin role is granted only by accepting a platform-admin invitation")
    void thePlatformAdminRoleIsGrantedOnlyByAcceptingAnInvitation() {
        noClasses()
                .that().doNotBelongToAnyOf(PlatformAdminInvitationAcceptance.class,
                        ManagePlatformAdminRepositoryDatabase.class)
                .should().callMethodWhere(target(owner(assignableTo(ManagePlatformAdminRepository.class)))
                        .and(target(name("grant"))))
                .because("the role is held only by the first admin created by /init and by people who accepted a "
                        + "platform-admin invitation with its confirmation code (ADR-0004, #447)")
                .check(IMPORTED_CLASSES);
        noClasses()
                .that().doNotBelongToAnyOf(UserEntity.class, ManagePlatformAdminRepositoryDatabase.class)
                .should().callMethodWhere(target(owner(assignableTo(UserEntity.class)))
                        .and(target(name("setPlatformAdmin"))))
                .because("the role is written only by its grant and revocation, and by /init's creation through "
                        + "UserEntity.createNewUser (ADR-0004, #447)")
                .check(IMPORTED_CLASSES);
    }
}
