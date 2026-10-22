package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactChannelDraft;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Channels and callers shared by the contact channel tests. Every list of channels is in an order that is neither
 * alphabetical by label nor by type, so a test that reads it back in the right order is not passing by accident.
 */
final class ContactChannelsFixtures {

    private ContactChannelsFixtures() {
    }

    static List<ContactChannelDraft> threeChannels() {
        return List.of(
                new ContactChannelDraft("PHONE", "Treasurer", "+34 600 111 222", "Weekdays, 18:00 to 20:00",
                        "Only urgent matters."),
                new ContactChannelDraft("ADDRESS", "Office", "Calle Mayor 1, Luco de Jiloca", null, null),
                new ContactChannelDraft("EMAIL", "General", "info@example.org", null, "Answered within a week."));
    }

    static List<ContactChannelDraft> twoOtherChannels() {
        return List.of(
                new ContactChannelDraft("EMAIL", "Secretary", "secretary@example.org", "Mondays", null),
                new ContactChannelDraft("PHONE", "Assembly", "976 000 000", null, null));
    }

    static User platformAdmin(CreateUserRepository createUserRepository,
                              ManagePlatformAdminRepository managePlatformAdminRepository) {
        User user = enabledUser(createUserRepository);
        managePlatformAdminRepository.grant(UserId.of(user.getId()));
        return user;
    }

    static User memberOf(UUID communityId, CommunityRole role, CreateUserRepository createUserRepository,
                         CreateMembershipRepository createMembershipRepository) {
        User user = enabledUser(createUserRepository);
        createMembershipRepository.create(communityId, user.getId(), role);
        return user;
    }

    static User enabledUser(CreateUserRepository createUserRepository) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }
}
