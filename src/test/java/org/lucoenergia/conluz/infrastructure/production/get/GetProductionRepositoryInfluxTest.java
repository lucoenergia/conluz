package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.production.get.GetProductionRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GetProductionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private GetProductionRepositoryInflux repository;

    @Test
    void testGetInstantProduction() {

        Double result = repository.getInstantProduction().getPower();

        Assertions.assertNotNull(result);
    }
}
