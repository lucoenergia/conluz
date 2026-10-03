package org.lucoenergia.conluz.infrastructure.shared.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadStatementCounterTest {

    private final ThreadStatementCounter inspector = new ThreadStatementCounter();

    @Test
    void countsTheStatementsPreparedOnTheMeasuringThread() {
        long statements = ThreadStatementCounter.count(() -> {
            inspector.inspect("select 1");
            inspector.inspect("select 2");
        });

        assertEquals(2, statements);
    }

    @Test
    void ignoresStatementsPreparedOnAnotherThreadDuringTheMeasurement() throws Exception {
        long statements = ThreadStatementCounter.count(() -> {
            inspector.inspect("select 1");
            Thread scheduledJob = new Thread(() -> inspector.inspect("select * from shelly_config"));
            scheduledJob.start();
            scheduledJob.join();
        });

        assertEquals(1, statements);
    }

    @Test
    void ignoresStatementsPreparedOutsideAMeasurement() {
        inspector.inspect("select before");

        long statements = ThreadStatementCounter.count(() -> inspector.inspect("select during"));
        inspector.inspect("select after");

        assertEquals(1, statements);
    }

    @Test
    void closesTheMeasurementWhenTheWorkFails() {
        assertThrows(IllegalStateException.class, () -> ThreadStatementCounter.count(() -> {
            throw new IllegalStateException("work failed");
        }));

        assertEquals(0, ThreadStatementCounter.count(() -> { }));
    }

    @Test
    void returnsTheSqlUnchanged() {
        assertEquals("select 1", inspector.inspect("select 1"));
    }

    @Test
    void refusesANestedMeasurement() {
        assertThrows(IllegalStateException.class,
                () -> ThreadStatementCounter.count(() -> ThreadStatementCounter.count(() -> { })));
    }
}
