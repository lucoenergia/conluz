package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannel;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelType;
import org.lucoenergia.conluz.domain.admin.community.contact.GetCommunityContactChannelsRepository;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.threeChannels;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.twoOtherChannels;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetCommunityContactControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/contact";

    /**
     * What {@link ContactChannelsFixtures#threeChannels()} is stored as, written out rather than derived so the test
     * does not share the code under test.
     */
    private static final List<ContactChannel> CHANNELS_OF_X = List.of(
            new ContactChannel(ContactChannelType.PHONE, "Treasurer", "+34 600 111 222", "Weekdays, 18:00 to 20:00",
                    "Only urgent matters."),
            new ContactChannel(ContactChannelType.ADDRESS, "Office", "Calle Mayor 1, Luco de Jiloca", null, null),
            new ContactChannel(ContactChannelType.EMAIL, "General", "info@example.org", null,
                    "Answered within a week."));
    private static final List<ContactChannel> CHANNELS_OF_Y = List.of(
            new ContactChannel(ContactChannelType.EMAIL, "Secretary", "secretary@example.org", "Mondays", null),
            new ContactChannel(ContactChannelType.PHONE, "Assembly", "976 000 000", null, null));

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private SetCommunityContactChannelsService setCommunityContactChannelsService;
    @Autowired
    private GetCommunityContactChannelsRepository getCommunityContactChannelsRepository;
    @Autowired
    private EntityManager entityManager;

    private Community communityX;
    private Community communityY;

    /**
     * Both communities start with channels, so "nothing changed" is checked against a non-empty list.
     */
    @BeforeEach
    void twoCommunitiesWithChannels() {
        communityX = createCommunityRepository.create(CommunityMother.random().build());
        communityY = createCommunityRepository.create(CommunityMother.random().build());
        setCommunityContactChannelsService.replace(communityX.getId(), threeChannels());
        setCommunityContactChannelsService.replace(communityY.getId(), twoOtherChannels());
        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
        assertThat(stored(communityY)).isEqualTo(CHANNELS_OF_Y);
    }

    // --- CTC-002: who may replace the list, and how ---

    @Test
    @DisplayName("CTC-002 a community admin replaces the list, which is returned and read back in the submitted order")
    void aCommunityAdminReplacesTheList() throws Exception {
        String admin = loginAsCommunityAdmin(communityX.getId());

        putContact(communityX.getId(), admin, List.of(
                channel("EMAIL", "Zeta", "zeta@example.org", null, "Write any time."),
                channel("ADDRESS", "Almacén", "Polígono 3, nave 2", "Saturdays", null),
                channel("PHONE", "Mobile", "+34 611 222 333", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(3)))
                .andExpect(jsonPath("$.channels[0].label").value("Zeta"))
                .andExpect(jsonPath("$.channels[0].schedule").value(nullValue()))
                .andExpect(jsonPath("$.channels[1].label").value("Almacén"))
                .andExpect(jsonPath("$.channels[1].notes").value(nullValue()))
                .andExpect(jsonPath("$.channels[2].label").value("Mobile"));

        List<ContactChannel> expected = List.of(
                new ContactChannel(ContactChannelType.EMAIL, "Zeta", "zeta@example.org", null, "Write any time."),
                new ContactChannel(ContactChannelType.ADDRESS, "Almacén", "Polígono 3, nave 2", "Saturdays", null),
                new ContactChannel(ContactChannelType.PHONE, "Mobile", "+34 611 222 333", null, null));
        assertThat(stored(communityX)).isEqualTo(expected);
        mockMvc.perform(get(String.format(URL_TEMPLATE, communityX.getId())).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(3)))
                .andExpect(jsonPath("$.channels[0].label").value("Zeta"))
                .andExpect(jsonPath("$.channels[1].label").value("Almacén"))
                .andExpect(jsonPath("$.channels[2].label").value("Mobile"));
        assertThat(stored(communityY)).isEqualTo(CHANNELS_OF_Y);
    }

    @Test
    @DisplayName("CTC-002 a non-empty list replaced by a different non-empty one takes the old positions without a "
            + "conflict")
    void aNonEmptyListIsReplacedByAnotherNonEmptyOne() throws Exception {
        String admin = loginAsCommunityAdmin(communityX.getId());

        // Positions 0 and 1 are taken by the old channels until they are deleted
        putContact(communityX.getId(), admin, List.of(
                channel("ADDRESS", "Office", "Calle Mayor 1, Luco de Jiloca", null, null),
                channel("PHONE", "Treasurer", "+34 600 111 222", "Weekdays, 18:00 to 20:00", "Only urgent matters.")))
                .andExpect(status().isOk());

        assertThat(stored(communityX)).containsExactly(CHANNELS_OF_X.get(1), CHANNELS_OF_X.get(0));
    }

    @Test
    @DisplayName("CTC-002 a community admin replaces the list with an empty one, removing every channel")
    void aCommunityAdminReplacesTheListWithAnEmptyOne() throws Exception {
        String admin = loginAsCommunityAdmin(communityX.getId());

        putContact(communityX.getId(), admin, List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(0)));

        assertThat(stored(communityX)).isEmpty();
        mockMvc.perform(get(String.format(URL_TEMPLATE, communityX.getId())).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(0)));
        assertThat(stored(communityY)).isEqualTo(CHANNELS_OF_Y);
    }

    @Test
    @DisplayName("CTC-002 a platform admin who is a community admin of the community replaces the list")
    void aPlatformAdminWhoIsACommunityAdminReplacesTheList() throws Exception {
        User platformAdmin = ContactChannelsFixtures.platformAdmin(createUserRepository, managePlatformAdminRepository);
        createMembershipRepository.create(communityX.getId(), platformAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        putContact(communityX.getId(), loginUser(platformAdmin), List.of())
                .andExpect(status().isOk());

        assertThat(stored(communityX)).isEmpty();
    }

    @Test
    @DisplayName("CTC-002 a member who is not a community admin gets 403 and nothing changes")
    void aMemberWhoIsNotAnAdminGets403() throws Exception {
        assertRefused(loginAsCommunityMember(communityX.getId()), 403);
    }

    @Test
    @DisplayName("CTC-002 a platform admin who is a member but not a community admin gets 403 and nothing changes")
    void aPlatformAdminWhoIsAPlainMemberGets403() throws Exception {
        User platformAdmin = ContactChannelsFixtures.platformAdmin(createUserRepository, managePlatformAdminRepository);
        createMembershipRepository.create(communityX.getId(), platformAdmin.getId(), CommunityRole.COMMUNITY_MEMBER);

        assertRefused(loginUser(platformAdmin), 403);
    }

    @Test
    @DisplayName("CTC-002 a user with no membership gets 404 and nothing changes")
    void aNonMemberGets404() throws Exception {
        assertRefused(loginAsPartner(), 404);
    }

    @Test
    @DisplayName("CTC-002 a community admin of another community gets 404 and neither community changes")
    void aCommunityAdminOfAnotherCommunityGets404() throws Exception {
        assertRefused(loginAsCommunityAdmin(communityY.getId()), 404);
    }

    @Test
    @DisplayName("CTC-002 a platform admin who is not a member gets 404 and nothing changes")
    void aPlatformAdminWhoIsNotAMemberGets404() throws Exception {
        User platformAdmin = ContactChannelsFixtures.platformAdmin(createUserRepository, managePlatformAdminRepository);

        assertRefused(loginUser(platformAdmin), 404);
    }

    @Test
    @DisplayName("CTC-002 a platform admin who administers another community gets 404 and nothing changes")
    void aPlatformAdminWhoAdministersAnotherCommunityGets404() throws Exception {
        User platformAdmin = ContactChannelsFixtures.platformAdmin(createUserRepository, managePlatformAdminRepository);
        createMembershipRepository.create(communityY.getId(), platformAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        assertRefused(loginUser(platformAdmin), 404);
    }

    @Test
    @DisplayName("CTC-002 a request without a token gets 401 and nothing changes")
    void withoutATokenGets401() throws Exception {
        mockMvc.perform(put(String.format(URL_TEMPLATE, communityX.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of())))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
    }

    @Test
    void aCommunityAdminWhoMustChangeTheirPasswordGets403() throws Exception {
        User admin = createCommunityAdminWhoMustChangePassword(communityX.getId());

        putContact(communityX.getId(), loginUser(admin), List.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));

        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
    }

    // --- CTC-003: what a list may hold ---

    @Test
    @DisplayName("CTC-003 eleven channels are refused with CONTACT_CHANNELS_TOO_MANY and nothing changes")
    void elevenChannelsAreRefused() throws Exception {
        List<ContactChannelBody> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(channel("PHONE", "Phone " + i, "600 000 00" + i, null, null));
        }

        assertInvalid(eleven, "CONTACT_CHANNELS_TOO_MANY", null)
                .andExpect(jsonPath("$.errors[0].params.maxChannels").value("10"));
    }

    @Test
    @DisplayName("CTC-003 ten channels are stored")
    void tenChannelsAreStored() throws Exception {
        List<ContactChannelBody> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add(channel("PHONE", "Phone " + i, "600 000 00" + i, null, null));
        }

        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), ten)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(10)));

        assertThat(stored(communityX)).hasSize(10);
    }

    @Test
    @DisplayName("CTC-003 a blank label is refused with CONTACT_CHANNEL_LABEL_BLANK and nothing changes")
    void aBlankLabelIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "", "600 000 000", null, null), "CONTACT_CHANNEL_LABEL_BLANK");
    }

    @Test
    @DisplayName("CTC-003 a value blank after trimming is refused with CONTACT_CHANNEL_VALUE_BLANK and nothing changes")
    void aValueBlankAfterTrimmingIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("ADDRESS", "Office", "   ", null, null), "CONTACT_CHANNEL_VALUE_BLANK");
    }

    @Test
    @DisplayName("CTC-003 an invalid email is refused with CONTACT_CHANNEL_EMAIL_INVALID and nothing changes")
    void anInvalidEmailIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("EMAIL", "General", "info.example.org", null, null),
                "CONTACT_CHANNEL_EMAIL_INVALID");
    }

    @Test
    @DisplayName("CTC-003 a phone number with letters is refused with CONTACT_CHANNEL_PHONE_CHARACTERS_INVALID and "
            + "nothing changes")
    void aPhoneWithLettersIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "Treasurer", "600 CALL ME", null, null),
                "CONTACT_CHANNEL_PHONE_CHARACTERS_INVALID");
    }

    @Test
    @DisplayName("CTC-003 a phone number with fewer than 6 digits is refused with CONTACT_CHANNEL_PHONE_TOO_FEW_DIGITS "
            + "and nothing changes")
    void aPhoneWithTooFewDigitsIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "Treasurer", "+34 12 3", null, null),
                "CONTACT_CHANNEL_PHONE_TOO_FEW_DIGITS")
                .andExpect(jsonPath("$.errors[0].params.minDigits").value("6"));
    }

    @Test
    @DisplayName("CTC-003 a label over 60 characters is refused with CONTACT_CHANNEL_LABEL_TOO_LONG and nothing changes")
    void aLabelOverItsLimitIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "a".repeat(61), "600 000 000", null, null),
                "CONTACT_CHANNEL_LABEL_TOO_LONG")
                .andExpect(jsonPath("$.errors[0].params.maxLength").value("60"));
    }

    @Test
    @DisplayName("CTC-003 a value over 200 characters is refused with CONTACT_CHANNEL_VALUE_TOO_LONG and nothing changes")
    void aValueOverItsLimitIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("ADDRESS", "Office", "a".repeat(201), null, null),
                "CONTACT_CHANNEL_VALUE_TOO_LONG")
                .andExpect(jsonPath("$.errors[0].params.maxLength").value("200"));
    }

    @Test
    @DisplayName("CTC-003 a schedule over 200 characters is refused with CONTACT_CHANNEL_SCHEDULE_TOO_LONG and nothing "
            + "changes")
    void aScheduleOverItsLimitIsRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "Treasurer", "600 000 000", "a".repeat(201), null),
                "CONTACT_CHANNEL_SCHEDULE_TOO_LONG")
                .andExpect(jsonPath("$.errors[0].params.maxLength").value("200"));
    }

    @Test
    @DisplayName("CTC-003 notes over 500 characters are refused with CONTACT_CHANNEL_NOTES_TOO_LONG and nothing changes")
    void notesOverTheirLimitAreRefused() throws Exception {
        assertInvalidSecondChannel(channel("PHONE", "Treasurer", "600 000 000", null, "a".repeat(501)),
                "CONTACT_CHANNEL_NOTES_TOO_LONG")
                .andExpect(jsonPath("$.errors[0].params.maxLength").value("500"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAX", "phone", "Email"})
    @DisplayName("CTC-003 a type not spelt exactly PHONE, EMAIL or ADDRESS is refused with CONTACT_CHANNEL_TYPE_INVALID "
            + "and nothing changes")
    void anUnknownTypeIsRefused(String type) throws Exception {
        assertInvalidSecondChannel(channel(type, "Office", "976 000 000", null, null), "CONTACT_CHANNEL_TYPE_INVALID");
    }

    @Test
    @DisplayName("CTC-003 a missing list is refused with CONTACT_CHANNELS_MISSING and nothing changes")
    void aMissingListIsRefused() throws Exception {
        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].code").value("CONTACT_CHANNELS_MISSING"));

        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
    }

    @Test
    @DisplayName("CTC-003 every broken rule is reported, each with its code and channel, and nothing changes")
    void everyBrokenRuleIsReported() throws Exception {
        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), "en", body(List.of(
                channel("PHONE", "", "600 000 000", null, null),
                channel("EMAIL", "General", "not an email", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The contact channels break 2 rule(s)."))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].code").value("CONTACT_CHANNEL_LABEL_BLANK"))
                .andExpect(jsonPath("$.errors[0].message").value("Channel 1: the label cannot be blank."))
                .andExpect(jsonPath("$.errors[0].params.index").value("0"))
                .andExpect(jsonPath("$.errors[1].code").value("CONTACT_CHANNEL_EMAIL_INVALID"))
                .andExpect(jsonPath("$.errors[1].message").value("Channel 2: the value is not a valid email address."))
                .andExpect(jsonPath("$.errors[1].params.index").value("1"));

        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
    }

    @Test
    @DisplayName("CTC-003 with Accept-Language es, a single broken rule is the message, in Spanish")
    void aSingleBrokenRuleIsTheMessage_inSpanish() throws Exception {
        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), "es", body(List.of(
                channel("PHONE", "Treasurer", "600 000 000", null, null),
                channel("PHONE", "", "600 000 000", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Canal 2: escribe un nombre para el canal."))
                .andExpect(jsonPath("$.errors[0].message").value("Canal 2: escribe un nombre para el canal."));
    }

    @Test
    @DisplayName("CTC-003 every text is trimmed, and a blank schedule or notes is stored as null")
    void textsAreTrimmed_andBlankScheduleAndNotesAreStoredAsNull() throws Exception {
        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), List.of(
                channel("EMAIL", "  General  ", " info@example.org ", "   ", ""),
                channel(" PHONE ", "Treasurer", "+34 600 111 222", "  Weekdays  ", "  Urgent only. ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0].label").value("General"))
                .andExpect(jsonPath("$.channels[0].schedule").value(nullValue()))
                .andExpect(jsonPath("$.channels[0].notes").value(nullValue()));

        assertThat(stored(communityX)).containsExactly(
                new ContactChannel(ContactChannelType.EMAIL, "General", "info@example.org", null, null),
                new ContactChannel(ContactChannelType.PHONE, "Treasurer", "+34 600 111 222", "Weekdays",
                        "Urgent only."));
    }

    @Test
    @DisplayName("CTC-003 every text at its length limit is stored")
    void textsAtTheirLimitsAreStored() throws Exception {
        putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), List.of(
                channel("ADDRESS", "a".repeat(60), "b".repeat(200), "c".repeat(200), "d".repeat(500))))
                .andExpect(status().isOk());

        assertThat(stored(communityX)).containsExactly(new ContactChannel(ContactChannelType.ADDRESS, "a".repeat(60),
                "b".repeat(200), "c".repeat(200), "d".repeat(500)));
    }

    // --- Helpers ---

    private void assertRefused(String authHeader, int expectedStatus) throws Exception {
        putContact(communityX.getId(), authHeader, List.of(channel("PHONE", "New", "600 999 999", null, null)))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.channels").doesNotExist());

        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
        assertThat(stored(communityY)).isEqualTo(CHANNELS_OF_Y);
    }

    /**
     * Puts a valid first channel ahead of {@code invalid}, so the violation is reported for the right channel.
     */
    private ResultActions assertInvalidSecondChannel(ContactChannelBody invalid, String code) throws Exception {
        return assertInvalid(List.of(channel("ADDRESS", "Office", "Calle Mayor 1", null, null), invalid), code, "1");
    }

    private ResultActions assertInvalid(List<ContactChannelBody> channels, String code, String index)
            throws Exception {
        ResultActions result = putContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId()), channels)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].code").value(code));
        if (index != null) {
            result.andExpect(jsonPath("$.errors[0].params.index").value(index));
        }
        assertThat(stored(communityX)).isEqualTo(CHANNELS_OF_X);
        return result;
    }

    private ResultActions putContact(UUID communityId, String authHeader, List<ContactChannelBody> channels)
            throws Exception {
        return putContact(communityId, authHeader, body(channels));
    }

    private ResultActions putContact(UUID communityId, String authHeader, String body) throws Exception {
        return mockMvc.perform(put(String.format(URL_TEMPLATE, communityId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }

    private ResultActions putContact(UUID communityId, String authHeader, String language, String body)
            throws Exception {
        return mockMvc.perform(put(String.format(URL_TEMPLATE, communityId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }

    private String body(List<ContactChannelBody> channels) throws Exception {
        return objectMapper.writeValueAsString(new SetCommunityContactBody(channels));
    }

    private static ContactChannelBody channel(String type, String label, String value, String schedule, String notes) {
        return new ContactChannelBody(type, label, value, schedule, notes);
    }

    /**
     * Flushed and cleared first, so what is read is what the database holds, with every pending insert sent to it.
     */
    private List<ContactChannel> stored(Community community) {
        entityManager.flush();
        entityManager.clear();
        return getCommunityContactChannelsRepository.findByCommunityId(community.getId());
    }
}
