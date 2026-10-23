package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.contact.SetCommunityContactChannelsService;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.memberOf;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.threeChannels;
import static org.lucoenergia.conluz.infrastructure.admin.community.contact.ContactChannelsFixtures.twoOtherChannels;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetCommunityContactControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/contact";

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

    private Community communityX;
    private Community communityY;

    /**
     * Both communities have channels, so a caller who is answered with the wrong community's list is caught.
     */
    @BeforeEach
    void twoCommunitiesWithChannels() {
        communityX = createCommunityRepository.create(CommunityMother.random().build());
        communityY = createCommunityRepository.create(CommunityMother.random().build());
        setCommunityContactChannelsService.replace(communityX.getId(), threeChannels());
        setCommunityContactChannelsService.replace(communityY.getId(), twoOtherChannels());
    }

    @Test
    @DisplayName("CTC-001 a member reads an empty list when the community has no channels")
    void aMemberReadsAnEmptyList() throws Exception {
        Community empty = createCommunityRepository.create(CommunityMother.random().build());

        getContact(empty.getId(), loginAsCommunityMember(empty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(0)));
    }

    @Test
    @DisplayName("CTC-001 a member reads the community's channels in their stored order, with null schedule and notes "
            + "when unset")
    void aMemberReadsTheChannelsInOrder() throws Exception {
        assertChannelsOfX(getContact(communityX.getId(), loginAsCommunityMember(communityX.getId())));
    }

    @Test
    @DisplayName("CTC-001 a community admin reads the community's channels")
    void aCommunityAdminReadsTheChannels() throws Exception {
        assertChannelsOfX(getContact(communityX.getId(), loginAsCommunityAdmin(communityX.getId())));
    }

    @Test
    @DisplayName("CTC-001 a platform admin who is not a member reads the community's channels")
    void aPlatformAdminWhoIsNotAMemberReadsTheChannels() throws Exception {
        User platformAdmin = ContactChannelsFixtures.platformAdmin(createUserRepository, managePlatformAdminRepository);

        assertChannelsOfX(getContact(communityX.getId(), loginUser(platformAdmin)));
    }

    @Test
    @DisplayName("CTC-001 a user with no membership gets 404")
    void aNonMemberGets404() throws Exception {
        getContact(communityX.getId(), loginAsPartner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.channels").doesNotExist());
    }

    @Test
    @DisplayName("CTC-001 a member of another community gets 404")
    void aMemberOfAnotherCommunityGets404() throws Exception {
        getContact(communityX.getId(), loginAsCommunityMember(communityY.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.channels").doesNotExist());
    }

    @Test
    @DisplayName("CTC-001 a community admin of another community gets 404")
    void aCommunityAdminOfAnotherCommunityGets404() throws Exception {
        User adminOfY = memberOf(communityY.getId(), CommunityRole.COMMUNITY_ADMIN, createUserRepository,
                createMembershipRepository);

        getContact(communityX.getId(), loginUser(adminOfY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.channels").doesNotExist());
    }

    @Test
    @DisplayName("CTC-001 a community that does not exist gets 404, the same as one the caller is not a member of")
    void anUnknownCommunityGets404() throws Exception {
        getContact(UUID.randomUUID(), loginAsCommunityMember(communityX.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CTC-001 a request without a token gets 401")
    void withoutATokenGets401() throws Exception {
        mockMvc.perform(get(String.format(URL_TEMPLATE, communityX.getId())))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.channels").doesNotExist());
    }

    @Test
    void aMemberWhoMustChangeTheirPasswordGets403() throws Exception {
        User member = createCommunityMemberWhoMustChangePassword(communityX.getId());

        getContact(communityX.getId(), loginUser(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));
    }

    private void assertChannelsOfX(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.channels", hasSize(3)))
                .andExpect(jsonPath("$.channels[0].type").value("PHONE"))
                .andExpect(jsonPath("$.channels[0].label").value("Treasurer"))
                .andExpect(jsonPath("$.channels[0].value").value("+34 600 111 222"))
                .andExpect(jsonPath("$.channels[0].schedule").value("Weekdays, 18:00 to 20:00"))
                .andExpect(jsonPath("$.channels[0].notes").value("Only urgent matters."))
                .andExpect(jsonPath("$.channels[1].type").value("ADDRESS"))
                .andExpect(jsonPath("$.channels[1].label").value("Office"))
                .andExpect(jsonPath("$.channels[1].value").value("Calle Mayor 1, Luco de Jiloca"))
                .andExpect(jsonPath("$.channels[1].schedule").value(nullValue()))
                .andExpect(jsonPath("$.channels[1].notes").value(nullValue()))
                .andExpect(jsonPath("$.channels[2].type").value("EMAIL"))
                .andExpect(jsonPath("$.channels[2].label").value("General"))
                .andExpect(jsonPath("$.channels[2].value").value("info@example.org"))
                .andExpect(jsonPath("$.channels[2].schedule").value(nullValue()))
                .andExpect(jsonPath("$.channels[2].notes").value("Answered within a week."))
                .andExpect(jsonPath("$.channels[0].id").doesNotExist());
    }

    private ResultActions getContact(UUID communityId, String authHeader) throws Exception {
        return mockMvc.perform(get(String.format(URL_TEMPLATE, communityId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print());
    }
}
