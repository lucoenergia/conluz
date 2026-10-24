package org.lucoenergia.conluz.infrastructure.admin.community.admins.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityAdminChangeEmailFactoryTest {

    private static final String RECIPIENT = "admin@example.org";
    private static final String COMMUNITY = "Calle Solar";
    private static final String AFFECTED = "Ana Afectada";
    private static final String ACTOR = "Bea Actora";
    /** 10:05 UTC in winter: 11:05 in Madrid, 10:05 in the Canary Islands. */
    private static final Instant WINTER = Instant.parse("2026-01-15T10:05:00Z");

    @ParameterizedTest(name = "{0}")
    @DisplayName("MEM-035 the email to the other admins says what changed, who made the change and to whom")
    @CsvSource(delimiter = '|', value = {
            "INVITED           | Bea Actora ha invitado a Ana Afectada a administrar Calle Solar.",
            "INVITATION_RESENT | Bea Actora ha vuelto a enviar a Ana Afectada la invitación para administrar Calle Solar.",
            "ACCEPTED          | Ana Afectada ha aceptado la invitación y ya administra Calle Solar.",
            "PROMOTED          | Bea Actora ha dado a Ana Afectada permisos de administración en Calle Solar.",
            "DEMOTED           | Bea Actora ha quitado a Ana Afectada los permisos de administración en Calle Solar.",
            "REMOVED           | Bea Actora ha eliminado a Ana Afectada de Calle Solar.",
            "SUSPENDED         | Bea Actora ha suspendido el acceso de Ana Afectada a la administración de Calle Solar.",
            "REACTIVATED       | Bea Actora ha reactivado el acceso de Ana Afectada a la administración de Calle Solar."
    })
    void theEmailToTheOtherAdmins_saysWhatChanged(CommunityAdminChange change, String sentence) {
        Email email = factory("Europe/Madrid").build(RECIPIENT, message(change, ACTOR, false), false);

        assertThat(email.body()).isEqualTo("Hola:\n\n" + sentence
                + "\n\nFecha: 15/01/2026, a las 11:05 (hora de Europa central)."
                + "\n\nSi no esperabas este cambio, habla con las demás personas que administran Calle Solar o "
                + "contacta con el operador de la plataforma."
                + "\n\nRecibes este mensaje porque administras Calle Solar en Conluz.");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("MEM-035 the email to the affected admin tells them, in the second person, what was done to them")
    @CsvSource(delimiter = '|', value = {
            "DEMOTED     | Bea Actora te ha quitado los permisos de administración en Calle Solar.",
            "REMOVED     | Bea Actora te ha eliminado de Calle Solar.",
            "SUSPENDED   | Bea Actora ha suspendido tu acceso a la administración de Calle Solar.",
            "REACTIVATED | Bea Actora ha reactivado tu acceso a la administración de Calle Solar."
    })
    void theEmailToTheAffectedAdmin_tellsThemWhatWasDone(CommunityAdminChange change, String sentence) {
        Email email = factory("Europe/Madrid").build(RECIPIENT, message(change, ACTOR, false), true);

        assertThat(email.body()).isEqualTo("Hola:\n\n" + sentence
                + "\n\nFecha: 15/01/2026, a las 11:05 (hora de Europa central)."
                + "\n\nSi no esperabas este cambio, habla con las personas que administran Calle Solar o "
                + "contacta con el operador de la plataforma.");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("MEM-035 a change the affected admin made themselves is told as theirs")
    @CsvSource(delimiter = '|', value = {
            "DEMOTED | Ana Afectada ha renunciado a sus permisos de administración en Calle Solar.",
            "REMOVED | Ana Afectada ha dejado Calle Solar."
    })
    void aChangeTheAffectedAdminMadeThemselves_isToldAsTheirs(CommunityAdminChange change, String sentence) {
        Email email = factory("Europe/Madrid").build(RECIPIENT, message(change, AFFECTED, true), false);

        assertThat(email.body()).startsWith("Hola:\n\n" + sentence + "\n\n");
    }

    @Test
    @DisplayName("MEM-035 a change made by the platform operator names the operator, not a person")
    void aChangeMadeByTheOperator_namesTheOperator() {
        CommunityAdminChangeEmailFactory factory = factory("Europe/Madrid");

        Email toOthers = factory.build(RECIPIENT, message(CommunityAdminChange.SUSPENDED, null, false), false);
        Email toAffected = factory.build(RECIPIENT, message(CommunityAdminChange.SUSPENDED, null, false), true);

        assertThat(toOthers.body()).contains("\n\nEl operador de la plataforma ha suspendido el acceso de "
                + "Ana Afectada a la administración de Calle Solar.\n\n");
        assertThat(toAffected.body()).contains("\n\nEl operador de la plataforma ha suspendido tu acceso a la "
                + "administración de Calle Solar.\n\n");
    }

    @Test
    @DisplayName("MEM-035 the email is addressed to the recipient, names the community in its subject and has its "
            + "own category")
    void theEmail_isAddressedAndCategorised() {
        Email email = factory("Europe/Madrid").build(RECIPIENT, message(CommunityAdminChange.PROMOTED, ACTOR, false),
                false);

        assertThat(email.recipient()).isEqualTo(RECIPIENT);
        assertThat(email.subject()).isEqualTo("Cambio en la administración de Calle Solar");
        assertThat(email.category()).isEqualTo(CommunityAdminChangeEmailFactory.CATEGORY);
    }

    @Test
    @DisplayName("MEM-035 the date and time of the change are those of the configured zone, which the email names")
    void theDateAndTime_areThoseOfTheConfiguredZone() {
        Email email = factory("Atlantic/Canary").build(RECIPIENT, message(CommunityAdminChange.PROMOTED, ACTOR, false),
                false);

        assertThat(email.body()).contains("\n\nFecha: 15/01/2026, a las 10:05 (hora de Europa occidental).\n\n");
    }

    @Test
    @DisplayName("MEM-035 the time of a change in summer follows the zone's summer offset, and a change near "
            + "midnight its date")
    void theTimeOfAChangeInSummer_followsTheZonesOffset() {
        Instant lateInSummer = Instant.parse("2026-07-31T22:30:00Z");
        CommunityAdminChangeEmailFactory.Message message = new CommunityAdminChangeEmailFactory.Message(
                CommunityAdminChange.PROMOTED, COMMUNITY, AFFECTED, ACTOR, false, lateInSummer);

        Email email = factory("Europe/Madrid").build(RECIPIENT, message, false);

        assertThat(email.body()).contains("\n\nFecha: 01/08/2026, a las 00:30 (hora de Europa central).\n\n");
    }

    // --- Fixtures ---

    private static CommunityAdminChangeEmailFactory.Message message(CommunityAdminChange change, String actorName,
                                                                    boolean byAffected) {
        return new CommunityAdminChangeEmailFactory.Message(change, COMMUNITY, AFFECTED, actorName, byAffected,
                WINTER);
    }

    private static CommunityAdminChangeEmailFactory factory(String zone) {
        TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of(zone));
        return new CommunityAdminChangeEmailFactory(messages(), timeConfiguration);
    }

    private static MessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return messages;
    }
}
