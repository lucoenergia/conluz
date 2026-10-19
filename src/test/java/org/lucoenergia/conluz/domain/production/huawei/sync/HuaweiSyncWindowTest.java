package org.lucoenergia.conluz.domain.production.huawei.sync;

import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuaweiSyncWindowTest {

    @Test
    void theDefaultLookbackIsSevenDays() {
        assertEquals(Period.ofDays(7), HuaweiSyncWindow.DEFAULT.lookback());
    }

    @Test
    void aNegativeLookbackIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HuaweiSyncWindow(Period.ofDays(-1)));
    }
}
