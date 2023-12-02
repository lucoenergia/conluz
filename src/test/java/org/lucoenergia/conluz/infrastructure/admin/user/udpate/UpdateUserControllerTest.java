package org.lucoenergia.conluz.infrastructure.admin.user.udpate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    void testUpdateUser() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        // Creates a user
        User user = new User();
        user.setId("12345678Z");
        user.setNumber(1);
        user.setFullName("John Doe");
        user.setAddress("Fake Street 123");
        user.setEmail("johndoe@email.com");
        user.setPhoneNumber("+34666555444");
        user.setEnabled(true);
        user.setRole(Role.ADMIN);
        createUserRepository.create(user, UserMother.randomPassword());

        // Modify data of the user
        User userModified = new User();
        userModified.setId("12345678Z");
        userModified.setNumber(2);
        userModified.setFullName("Alice Smith");
        userModified.setAddress("Fake Street 666");
        userModified.setEmail("alicesmith@email.com");
        userModified.setPhoneNumber("+34666555111");
        userModified.setEnabled(true);
        userModified.setRole(Role.PARTNER);
        String body = objectMapper.writeValueAsString(userModified);

        mockMvc.perform(put(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("12345678Z"))
                .andExpect(jsonPath("$.number").value("2"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.address").value("Fake Street 666"))
                .andExpect(jsonPath("$.email").value("alicesmith@email.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+34666555111"))
                .andExpect(jsonPath("$.role").value(Role.PARTNER.name()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
