package org.lucoenergia.conluz.infrastructure.admin.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;

@Transactional
class InitControllerTest extends BaseControllerTest {

    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testInit() throws Exception {

        init();

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(PERSONAL_ID)));
    }

    @Test
    void testInitCannotBeExecutedTwice() throws Exception {

        init();

        MvcResult result = init();
        Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
    }
}
