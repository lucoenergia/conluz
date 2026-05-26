package org.lucoenergia.conluz.domain.admin.supply.create;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create.CreateSupplyPartitionDto;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.ValidateSupplyPartitionsServiceImpl;
import org.springframework.context.MessageSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ValidateSupplyPartitionsServiceTest {

    private ValidateSupplyPartitionsService service;
    private final MessageSource messageSource = mock(MessageSource.class);;

    @BeforeEach
    void setUp() {
        service = new ValidateSupplyPartitionsServiceImpl(messageSource);
    }

    @Test
    void testValidateTotalCoefficientSuccess() {
        // arrange
        List<CreateSupplyPartitionDto> suppliesPartitions = new ArrayList<>();

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("SUPPLY1");
        dto1.setCoefficient(0.5);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("SUPPLY2");
        dto2.setCoefficient(0.5);

        suppliesPartitions.add(dto1);
        suppliesPartitions.add(dto2);

        // act and assert
        assertDoesNotThrow(() -> service.validateTotalCoefficient(suppliesPartitions));
    }

    @Test
    void testValidateTotalCoefficientFailure() {
        // arrange
        List<CreateSupplyPartitionDto> suppliesPartitions = new ArrayList<>();

        CreateSupplyPartitionDto dto1 = new CreateSupplyPartitionDto();
        dto1.setCode("SUPPLY1");
        dto1.setCoefficient(0.4);

        CreateSupplyPartitionDto dto2 = new CreateSupplyPartitionDto();
        dto2.setCode("SUPPLY2");
        dto2.setCoefficient(0.5);

        suppliesPartitions.add(dto1);
        suppliesPartitions.add(dto2);

        // act and assert
        assertThrows(InvalidSupplyPartitionCoefficientException.class, () -> service.validateTotalCoefficient(suppliesPartitions));
    }
}
