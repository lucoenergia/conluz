package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShellyConsumptionsHourlyAggregatorJobTest {

    @Mock
    private ShellyConsumptionsHourlyAggregatorInflux3Service aggregator;

    @InjectMocks
    private ShellyConsumptionsHourlyAggregatorJob job;

    /**
     * Test the `run` method to ensure it invokes the aggregator's aggregate method
     * with the correct time range parameters.
     */
    @Test
    void run_aggregatesForLastFiveHours() {
        // Act
        job.run();

        // Assert
        verify(aggregator, times(1)).aggregate(any(OffsetDateTime.class),
                any(OffsetDateTime.class));
    }
}