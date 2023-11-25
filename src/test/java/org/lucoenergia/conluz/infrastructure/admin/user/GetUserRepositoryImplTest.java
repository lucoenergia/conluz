package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.get.GetUserRepositoryImpl;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

@SpringBootTest
@Transactional
class GetUserRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private GetUserRepositoryImpl getUserRepository;
    @Autowired
    private UserRepository userRepository;


    @Test
    void testFindById() {

        UserEntity userOne = UserMother.randomUserEntity();
        // Create some users
        userRepository.saveAll(Arrays.asList(
                userOne,
                UserMother.randomUserEntity(),
                UserMother.randomUserEntity()
        ));
        UserId userId = new UserId(userOne.getId());

        Optional<User> result = getUserRepository.findById(userId);

        Assertions.assertTrue(result.isPresent());
    }
}