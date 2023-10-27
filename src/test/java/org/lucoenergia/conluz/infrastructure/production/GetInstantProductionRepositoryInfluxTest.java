package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GetInstantProductionRepositoryInfluxTest {

    @Autowired
    private GetInstantProductionRepositoryInflux repository;

    @Test
    void testGetInstantProduction() {

        Double result = repository.getInstantProduction().getPower();

        Assertions.assertNotNull(result);
    }
}
