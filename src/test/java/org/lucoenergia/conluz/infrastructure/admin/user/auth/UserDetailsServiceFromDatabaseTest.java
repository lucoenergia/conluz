package org.lucoenergia.conluz.infrastructure.admin.user.auth;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserDetailsServiceFromDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    void testUserNotFound() {

        UsernameNotFoundException exception = Assertions.assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("unknown"));

        Assertions.assertEquals("unknown", exception.getMessage());
    }

    @Test
    void testUserFound() {

        // Create a user
        User user = createUserRepository.create(UserMother.randomUser(), "A good pa!!w0rd");

        User result = (User) userDetailsService.loadUserByUsername(user.getId().toString());

        Assertions.assertEquals(user.getId(), result.getId());
        Assertions.assertEquals(user.getPersonalId(), result.getPersonalId());
        Assertions.assertEquals(user.getUsername(), result.getUsername());
        Assertions.assertEquals(user.getAddress(), result.getAddress());
        Assertions.assertEquals(user.getEmail(), result.getEmail());
        Assertions.assertEquals(user.getFullName(), result.getFullName());
        Assertions.assertEquals(user.getNumber(), result.getNumber());
        Assertions.assertEquals(user.getPassword(), result.getPassword());
        Assertions.assertEquals(user.getPhoneNumber(), result.getPhoneNumber());
        Assertions.assertEquals(user.getAuthorities(), result.getAuthorities());
    }
}
