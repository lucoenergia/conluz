package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.infrastructure.admin.community.admins.notification.CommunityAdminChangeEmailFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Telling a community's admins about every change to its admins (#466): who is told, what they are told, and that
 * the change succeeds whatever happens to the notification.
 *
 * <p>Who is told is read from the emails handed to the sender, which a request hands over before it answers; the
 * emails that leave through the embedded SMTP server are then awaited, so every test also sends them for real.</p>
 */
class CommunityAdminChangeNotificationControllerTest extends BaseCommunityAdminTest {

    private static final String WARN_PREFIX = "Admin change ";

    @Autowired
    private DisableUserService disableUserService;
    @Autowired
    private CreateSupplyService createSupplyService;

    /** The full names of the people of each test: they may appear in an email body, never in a log line. */
    private final List<String> names = new ArrayList<>();

    @AfterEach
    void noLogLineContainsAName() {
        for (ILoggingEvent event : logs.all()) {
            if (event.getLoggerName().startsWith("com.icegreen.greenmail")) {
                continue;
            }
            for (String name : names) {
                assertThat(event.getFormattedMessage()).as("a line logged by %s", event.getLoggerName())
                        .doesNotContain(name);
            }
        }
    }

    // --- AC1, AC7: invitations ---

    @Test
    @DisplayName("MEM-035 an admin invitation and its resend are told to the community's admins, never to the invited "
            + "admin")
    void anInvitationAndItsResend_areToldToTheAdmins() throws Exception {
        Community community = newCommunity();
        User first = admin(community);
        User second = admin(community);
        String platformAdmin = loginAsPlatformAdmin();
        Map<String, Object> record = adminRecord("Invitada Uno");

        InvitedAdmin invited = invitedAdminOf(community, platformAdmin, record);

        List<Email> invitation = nextAdminChangeNotifications();
        assertThat(recipients(invitation)).containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(bodyTo(invitation, first.getEmail()))
                .contains("El operador de la plataforma ha invitado a " + record.get("fullName") + " a administrar "
                        + community.getName() + ".")
                .contains("Si no esperabas este cambio, habla con las demás personas que administran "
                        + community.getName() + " o contacta con el operador de la plataforma.")
                .contains("Recibes este mensaje porque administras " + community.getName() + " en Conluz.");
        assertThat(invitation.get(0).subject()).isEqualTo("Cambio en la administración de " + community.getName());

        resendAdmin(platformAdmin, community.getId(), invited.userId()).andExpect(status().isOk());

        List<Email> resend = nextAdminChangeNotifications();
        assertThat(recipients(resend)).containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(bodyTo(resend, second.getEmail())).contains("El operador de la plataforma ha vuelto a enviar a "
                + record.get("fullName") + " la invitación para administrar " + community.getName() + ".");
        MimeMessage[] sent = awaitNextEmailsHandedOver();
        assertThat(sent).hasSize(3);
        tokenFrom(emailTo(sent, invited.contactEmail()));
    }

    @Test
    @DisplayName("MEM-035 a platform admin inviting its own account is sent the invitation, and the change is told to "
            + "the community's admins")
    void aPlatformAdminInvitingItself_isToldToTheAdmins() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        User platformAdmin = newPlatformAdmin();
        remember(platformAdmin.getPersonalId());
        Map<String, Object> record = adminRecord("Operadora Invitada");
        record.put("personalId", platformAdmin.getPersonalId());

        InvitedAdmin invited = invitedAdminOf(community, loginUser(platformAdmin), record);

        assertThat(invited.userId()).isEqualTo(platformAdmin.getId());
        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(admin.getEmail());
        assertThat(bodyTo(notifications, admin.getEmail())).contains("El operador de la plataforma ha invitado a "
                + record.get("fullName") + " a administrar " + community.getName() + ".");
    }

    // --- AC2: promotion ---

    @Test
    @DisplayName("MEM-035 a member promoted by a community admin is told to the other admins, and not to the actor")
    void aPromotion_isToldToTheOtherAdmins() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        User member = person(community, CommunityRole.COMMUNITY_MEMBER);

        changeRole(loginUser(actor), community, member, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(other.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha dado a "
                + member.getFullName() + " permisos de administración en " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    // --- AC3: losing and regaining access ---

    @Test
    @DisplayName("MEM-035 an admin demoted is told to the other admins and to the demoted admin, not to the actor, "
            + "and logged after the commit")
    void aDemotion_isToldToTheOthersAndTheAffected() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        User affected = admin(community);

        changeRole(loginUser(actor), community, affected, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactlyInAnyOrder(other.getEmail(), affected.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha quitado a "
                + affected.getFullName() + " los permisos de administración en " + community.getName() + ".");
        assertThat(bodyTo(notifications, affected.getEmail()))
                .contains(actor.getFullName() + " te ha quitado los permisos de administración en "
                        + community.getName() + ".")
                .contains("Si no esperabas este cambio, habla con las personas que administran "
                        + community.getName() + " o contacta con el operador de la plataforma.")
                .doesNotContain("Recibes este mensaje porque administras");
        assertThat(eventLines()).containsExactly("Admin change notified: communityId=" + community.getId()
                + ", userId=" + affected.getId() + ", change=DEMOTED, actorId=" + actor.getId() + ", emails=2");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 an admin removed is told to the other admins and to the removed admin, not to the actor")
    void aRemoval_isToldToTheOthersAndTheAffected() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        User affected = admin(community);

        remove(loginUser(actor), community, affected.getId()).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactlyInAnyOrder(other.getEmail(), affected.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha eliminado a "
                + affected.getFullName() + " de " + community.getName() + ".");
        assertThat(bodyTo(notifications, affected.getEmail())).contains(actor.getFullName() + " te ha eliminado de "
                + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 an admin suspended and reactivated by the platform admin is told to the other admins and to "
            + "the affected admin, never to the platform admin, even when it is an admin of the community")
    void aSuspensionAndAReactivation_areToldToTheOthersAndTheAffected() throws Exception {
        Community community = newCommunity();
        User other = admin(community);
        User affected = admin(community);
        User platformAdmin = newPlatformAdmin();
        remember(platformAdmin.getPersonalId());
        createMembershipRepository.create(community.getId(), platformAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(platformAdmin);

        disableAdmin(bearer, community.getId(), affected.getId()).andExpect(status().isOk());

        List<Email> suspension = nextAdminChangeNotifications();
        assertThat(recipients(suspension)).containsExactlyInAnyOrder(other.getEmail(), affected.getEmail());
        assertThat(bodyTo(suspension, other.getEmail())).contains("El operador de la plataforma ha suspendido el "
                + "acceso de " + affected.getFullName() + " a la administración de " + community.getName() + ".");
        assertThat(bodyTo(suspension, affected.getEmail())).contains("El operador de la plataforma ha suspendido tu "
                + "acceso a la administración de " + community.getName() + ".");

        enableAdmin(bearer, community.getId(), affected.getId()).andExpect(status().isOk());

        List<Email> reactivation = nextAdminChangeNotifications();
        assertThat(recipients(reactivation)).containsExactlyInAnyOrder(other.getEmail(), affected.getEmail());
        assertThat(bodyTo(reactivation, other.getEmail())).contains("El operador de la plataforma ha reactivado el "
                + "acceso de " + affected.getFullName() + " a la administración de " + community.getName() + ".");
        assertThat(bodyTo(reactivation, affected.getEmail())).contains("El operador de la plataforma ha reactivado "
                + "tu acceso a la administración de " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    // --- The affected admin, and who counts as a recipient ---

    @Test
    @DisplayName("MEM-035 an admin demoting themselves is told to the other admins, and not to themselves")
    void aSelfDemotion_isToldToTheOthersOnly() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);

        changeRole(loginUser(actor), community, actor, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(other.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName()
                + " ha renunciado a sus permisos de administración en " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 a pending admin removed is told to the other admins only: they never had access")
    void aPendingAdminRemoved_isToldToTheOthersOnly() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        Map<String, Object> record = adminRecord("Pendiente Eliminada");
        InvitedAdmin pending = invitedAdminOf(community, loginAsPlatformAdmin(), record);
        nextAdminChangeNotifications();

        remove(loginUser(actor), community, pending.userId()).andExpect(status().isOk());

        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(recipients(notifications)).containsExactly(other.getEmail());
        assertThat(bodyTo(notifications, other.getEmail())).contains(actor.getFullName() + " ha eliminado a "
                + record.get("fullName") + " de " + community.getName() + ".");
        awaitNextEmailsHandedOver();
    }

    @Test
    @DisplayName("MEM-035 a pending admin, a suspended admin and an admin whose account is disabled are not told")
    void adminsWhoCannotAct_areNotTold() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User active = admin(community);
        User suspended = admin(community);
        User disabledAccount = admin(community);
        String platformAdmin = loginAsPlatformAdmin();
        InvitedAdmin pending = invitedAdminOf(community, platformAdmin, adminRecord("Pendiente Callada"));
        disableAdmin(platformAdmin, community.getId(), suspended.getId()).andExpect(status().isOk());
        disableUserService.disable(UserId.of(disabledAccount.getId()));
        User member = person(community, CommunityRole.COMMUNITY_MEMBER);
        nextAdminChangeNotifications();
        awaitNextEmailsHandedOver();

        changeRole(loginUser(actor), community, member, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());

        assertThat(recipients(nextAdminChangeNotifications())).containsExactly(active.getEmail())
                .doesNotContain(pending.contactEmail(), suspended.getEmail(), disabledAccount.getEmail());
        awaitNextEmailsHandedOver();
    }

    // --- AC4: no contact email ---

    @Test
    @DisplayName("MEM-035 an admin without a contact email is sent nothing, and the change succeeds")
    void anAdminWithoutAContactEmail_isSentNothing() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User withEmail = admin(community);
        User withoutEmail = withoutContactEmail(community);
        User member = person(community, CommunityRole.COMMUNITY_MEMBER);
        String bearer = loginUser(actor);

        changeRole(bearer, community, member, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());
        assertThat(recipients(nextAdminChangeNotifications())).containsExactly(withEmail.getEmail());

        // Demoting the admin without a contact email tells the others, and them nothing
        changeRole(bearer, community, withoutEmail, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());
        assertThat(recipients(nextAdminChangeNotifications())).containsExactlyInAnyOrder(withEmail.getEmail(),
                member.getEmail());
        awaitNextEmailsHandedOver();
    }

    // --- AC5: a failure never changes the outcome ---

    @Test
    @DisplayName("MEM-035 every change succeeds when its notification cannot be handed over, and the failure is "
            + "logged without personal data")
    void everyChangeSucceeds_whenTheNotificationFails() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        // Another admin, so that every change has someone to tell
        admin(community);
        User target = admin(community);
        User member = person(community, CommunityRole.COMMUNITY_MEMBER);
        String platformAdmin = loginAsPlatformAdmin();
        String bearer = loginUser(actor);
        doThrow(new IllegalStateException("sending failed")).when(emailSender)
                .send(argThat(email -> email != null && CommunityAdminChangeEmailFactory.CATEGORY.equals(
                        email.category())));
        logs.clear();

        Map<String, Object> record = adminRecord("Invitada Fallida");
        String response = inviteAdmin(platformAdmin, community.getId(), record).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID invited = UUID.fromString(objectMapper.readTree(response).get("user").get("id").asText());
        resendAdmin(platformAdmin, community.getId(), invited).andExpect(status().isOk());
        changeRole(bearer, community, member, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());
        changeRole(bearer, community, member, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());
        disableAdmin(platformAdmin, community.getId(), target.getId()).andExpect(status().isOk());
        enableAdmin(platformAdmin, community.getId(), target.getId()).andExpect(status().isOk());
        remove(bearer, community, target.getId()).andExpect(status().isOk());

        assertThat(role(community, member.getId())).isEqualTo("COMMUNITY_MEMBER");
        assertThat(membershipCount(target.getPersonalId())).isZero();
        assertThat(logs.all())
                .filteredOn(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().startsWith(WARN_PREFIX))
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        failure("INVITED", community), failure("INVITATION_RESENT", community),
                        failure("PROMOTED", community), failure("DEMOTED", community),
                        failure("SUSPENDED", community), failure("REACTIVATED", community),
                        failure("REMOVED", community));
        assertThat(eventLines()).isEmpty();
        // The invitation and its resend still leave
        MimeMessage[] invitations = awaitNextEmails(2);
        assertThat(invitations).allSatisfy(sent -> assertThat(sent.getAllRecipients()[0].toString())
                .isEqualTo(record.get("contactEmail")));
    }

    // --- AC6: no personal data ---

    @Test
    @DisplayName("MEM-036 no email of any change contains a DNI, a contact email or a phone")
    void noEmailContainsPersonalData() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        User target = admin(community);
        String platformAdmin = loginAsPlatformAdmin();
        String bearer = loginUser(actor);

        InvitedAdmin invited = invitedAdminOf(community, platformAdmin, adminRecord("Invitada Discreta"));
        resendAdmin(platformAdmin, community.getId(), invited.userId()).andExpect(status().isOk());
        changeRole(bearer, community, target, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());
        changeRole(bearer, community, target, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());
        disableAdmin(platformAdmin, community.getId(), target.getId()).andExpect(status().isOk());
        enableAdmin(platformAdmin, community.getId(), target.getId()).andExpect(status().isOk());
        remove(bearer, community, target.getId()).andExpect(status().isOk());

        // nextNotifications checks every email against the DNIs, contact emails and phones remembered. Invited and
        // resent: the three admins; demoted: the other admin and the target; promoted: the other admin;
        // suspended and reactivated: the three admins; removed: the other admin and the target
        List<Email> notifications = nextAdminChangeNotifications();
        assertThat(notifications).hasSize(3 + 3 + 2 + 1 + 3 + 3 + 2);
        assertThat(recipients(notifications)).contains(other.getEmail());
        for (MimeMessage sent : awaitNextEmailsHandedOver()) {
            if (sent.getSubject().startsWith("Cambio en la administración")) {
                assertNoSecretIn(sent.getSubject() + "\n" + bodyOf(sent));
            }
        }
    }

    // --- Refused and unchanged ---

    @Test
    @DisplayName("MEM-035 a change refused by its own rules tells no one")
    void aRefusedChange_tellsNoOne() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        // Another admin, who would be told if the removal went through
        admin(community);
        User owner = admin(community);
        createSupplyService.create(SupplyMother.random(owner).build(), UserPersonalId.of(owner.getPersonalId()),
                community.getId());

        remove(loginUser(actor), community, owner.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_OWNS_SUPPLIES"));

        assertThat(nextAdminChangeNotifications()).isEmpty();
        assertThat(eventLines()).isEmpty();
    }

    @Test
    @DisplayName("MEM-035 a request that changes nothing, or changes a member, tells no one")
    void aRequestThatChangesNoAdmin_tellsNoOne() throws Exception {
        Community community = newCommunity();
        User actor = admin(community);
        User other = admin(community);
        User member = person(community, CommunityRole.COMMUNITY_MEMBER);
        String bearer = loginUser(actor);
        String platformAdmin = loginAsPlatformAdmin();

        changeRole(bearer, community, other, CommunityRole.COMMUNITY_ADMIN).andExpect(status().isOk());
        enableAdmin(platformAdmin, community.getId(), other.getId()).andExpect(status().isOk());
        changeRole(bearer, community, member, CommunityRole.COMMUNITY_MEMBER).andExpect(status().isOk());
        remove(bearer, community, member.getId()).andExpect(status().isOk());

        assertThat(nextAdminChangeNotifications()).isEmpty();
        assertThat(eventLines()).isEmpty();
    }

    // --- Fixtures ---

    /**
     * An active admin of the community, with an enabled account and a contact email.
     */
    private User admin(Community community) {
        return person(community, CommunityRole.COMMUNITY_ADMIN);
    }

    private User person(Community community, CommunityRole role) {
        User user = memberOf(community, role);
        remember(user.getPersonalId());
        remember(user.getPhoneNumber());
        names.add(user.getFullName());
        return user;
    }

    /**
     * An active admin whose member record has no contact email, as memberships stored before an admin needed one
     * are. Created without {@link #newUser()}, which always gives the account an email.
     */
    private User withoutContactEmail(Community community) {
        User user = UserMother.randomUser();
        user.enable();
        user.setEmail(null);
        remember(user.getPassword());
        remember(user.getPersonalId());
        remember(user.getPhoneNumber());
        createUserRepository.create(user);
        deleteAfterwards(user.getPersonalId());
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        names.add(user.getFullName());
        return user;
    }

    private Map<String, Object> adminRecord(String fullName) {
        Map<String, Object> record = newAdminRecord();
        record.put("fullName", fullName + " " + UUID.randomUUID());
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        names.add((String) record.get("fullName"));
        return record;
    }

    // --- Requests ---

    private ResultActions changeRole(String bearer, Community community, User user, CommunityRole role)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(),
                user.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role.name() + "\"}"));
    }

    private ResultActions remove(String bearer, Community community, UUID userId) throws Exception {
        return mockMvc.perform(delete("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(),
                userId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    // --- Notifications ---

    private List<String> eventLines() {
        return logs.all().stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Admin change notified:"))
                .toList();
    }

    private static String failure(String change, Community community) {
        return WARN_PREFIX + change + " in community " + community.getId() + " not notified: IllegalStateException";
    }

    private String role(Community community, UUID userId) {
        return jdbcTemplate.queryForObject("SELECT role FROM community_memberships WHERE community_id = ? "
                + "AND user_id = ?", String.class, community.getId(), userId);
    }
}
