package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptMembershipRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.DisableCommunityAdminServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.EnableCommunityAdminServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.InviteCommunityAdminServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.ResendCommunityAdminInvitationServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DeleteMembershipServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DisableMembershipServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.EnableMembershipServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.accept.CommunityInvitationAcceptance;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.FirstInvitationStep;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationResending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.ResendInvitationServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.SendInvitationServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.MemberImportRowStep;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.MembershipRegistration;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.RegisterMemberServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.delete.DeleteUserServiceImpl;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that every change to a community's admins is told to its admins (#466, MEM-035).
 *
 * <p>A membership's role and state are written through a few ports, and a pending admin is created and sent its
 * invitation through two helpers. Every class that calls one of them either is one of the services that change a
 * community's admins, and must then call {@link CommunityAdminChangeNotifier}, or is listed here with the reason it
 * changes no admin. A new caller fails the build until it is classified, so a new path that changes admins cannot
 * forget to notify them by accident.</p>
 */
public class CommunityAdminChangeNotificationArchTest extends BaseArchTest {

    /** The services that change a community's admins: each calls the notifier. */
    private static final Class<?>[] ADMIN_CHANGING_SERVICES = {
            InviteCommunityAdminServiceImpl.class,
            ResendCommunityAdminInvitationServiceImpl.class,
            CommunityInvitationAcceptance.class,
            UpdateMembershipRoleServiceImpl.class,
            DeleteMembershipServiceImpl.class,
            DisableCommunityAdminServiceImpl.class,
            EnableCommunityAdminServiceImpl.class,
    };

    /** The other callers of the writers below, none of which changes a community's admins. */
    private static final Class<?>[] NOT_ADMIN_CHANGES = {
            // Disable and enable only memberships with the member role (setEnabled filters on COMMUNITY_MEMBER)
            DisableMembershipServiceImpl.class,
            EnableMembershipServiceImpl.class,
            // Register members, always with the member role
            RegisterMemberServiceImpl.class,
            MemberImportRowStep.class,
            // Send or resend an invitation of a membership that exists already: no role or state changes. A
            // community admin may send one to a pending admin through them, which is not one of #466's events
            SendInvitationServiceImpl.class,
            FirstInvitationStep.class,
            ResendInvitationServiceImpl.class,
            // The resending rules, shared by the member and the admin resend; its callers are checked here
            InvitationResending.class,
            // Deletes every membership of an account, admins included, but no request can reach it: its guard
            // refuses every caller until account deletion is designed (#325)
            DeleteUserServiceImpl.class,
    };

    @Test
    @DisplayName("MEM-035 every service that changes a community's admins notifies them")
    void everyServiceThatChangesAdmins_notifiesThem() {
        classes()
                .that().belongToAnyOf(ADMIN_CHANGING_SERVICES)
                .should().callMethodWhere(target(owner(equivalentTo(CommunityAdminChangeNotifier.class)))
                        .and(target(name("notify"))))
                .because("every change to a community's admins is told to its admins (MEM-035, #466)")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("MEM-035 a new caller of a writer of admin memberships is classified")
    void everyWriterOfAdminMemberships_isClassified() {
        noClasses()
                .that().doNotBelongToAnyOf(ADMIN_CHANGING_SERVICES)
                .and().doNotBelongToAnyOf(NOT_ADMIN_CHANGES)
                .should().callMethodWhere(writesAMembershipsRoleOrState())
                .because("a class that writes a membership's role or state may change a community's admins, which "
                        + "must be told to them (MEM-035, #466): call CommunityAdminChangeNotifier and add the "
                        + "class to ADMIN_CHANGING_SERVICES, or add it to NOT_ADMIN_CHANGES with the reason")
                .check(IMPORTED_CLASSES);
    }

    private static DescribedPredicate<JavaCall<?>> writesAMembershipsRoleOrState() {
        return target(owner(equivalentTo(UpdateMembershipRoleRepository.class)
                .or(equivalentTo(DeleteMembershipRepository.class))
                .or(equivalentTo(SetMembershipEnabledRepository.class))
                .or(equivalentTo(AcceptMembershipRepository.class))))
                .or(target(owner(equivalentTo(MembershipRegistration.class))).and(target(name("register"))))
                .or(target(owner(equivalentTo(InvitationSending.class))).and(target(name("issueAndSend"))))
                .or(target(owner(equivalentTo(InvitationResending.class))).and(target(name("resend"))));
    }
}
