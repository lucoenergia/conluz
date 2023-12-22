package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

@Transactional
class GetUserRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private GetUserRepositoryImpl getUserRepository;
    @Autowired
    private UserRepository userRepository;


    @Test
    void testFindByPersonalId() {

        UserEntity userOne = UserMother.randomUserEntity();
        // Create some users
        userRepository.saveAll(Arrays.asList(
                userOne,
                UserMother.randomUserEntity(),
                UserMother.randomUserEntity()
        ));
        UserPersonalId userPersonalId = UserPersonalId.of(userOne.getPersonalId());

        Optional<User> result = getUserRepository.findByPersonalId(userPersonalId);

        Assertions.assertTrue(result.isPresent());
    }
}