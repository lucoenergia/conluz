package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the median an observed β is reported as (OBS-009), written from the rule and the acceptance
 * criteria of #597.
 */
class ObservedBetaTest {

    @Test
    @DisplayName("OBS-009 an odd count reports its middle value")
    void oddCountReportsTheMiddleValue() {
        ObservedBeta beta = ObservedBeta.of(List.of(0.03, 0.01, 0.05, 0.02, 0.04));

        assertThat(beta).isEqualTo(new ObservedBeta(new BigDecimal("0.030000"), 5));
    }

    @Test
    @DisplayName("OBS-009 an even count reports the mean of its two middle values")
    void evenCountReportsTheMeanOfTheTwoMiddleValues() {
        ObservedBeta beta = ObservedBeta.of(List.of(0.04, 0.01, 0.03, 0.02));

        assertThat(beta).isEqualTo(new ObservedBeta(new BigDecimal("0.025000"), 4));
    }

    @Test
    @DisplayName("OBS-009 a single value is its own median")
    void singleValueIsItsOwnMedian() {
        ObservedBeta beta = ObservedBeta.of(List.of(0.0412));

        assertThat(beta).isEqualTo(new ObservedBeta(new BigDecimal("0.041200"), 1));
    }

    @Test
    @DisplayName("OBS-009 no value reports null over 0 days")
    void noValueIsNull() {
        assertThat(ObservedBeta.of(List.of())).isEqualTo(new ObservedBeta(null, 0));
    }

    @Test
    @DisplayName("OBS-009 the median is rounded half-up to 6 decimals, whether it is a value or a mean")
    void roundedHalfUpToSixDecimals() {
        assertThat(ObservedBeta.of(List.of(0.0300005)).value()).isEqualTo(new BigDecimal("0.030001"));
        assertThat(ObservedBeta.of(List.of(0.030000, 0.030001)).value()).isEqualTo(new BigDecimal("0.030001"));
        assertThat(ObservedBeta.of(List.of(0.0300004)).value()).isEqualTo(new BigDecimal("0.030000"));
    }

    @Test
    @DisplayName("OBS-009 one outlying day does not move the median of ten")
    void outlierDoesNotMoveTheMedian() {
        List<Double> days = List.of(0.0300, 0.0302, 0.0298, 0.0301, 0.0300, 0.0299, 0.0300, 0.0303, 0.0297);
        List<Double> withOutlier = new ArrayList<>(days);
        withOutlier.add(0.0412);
        List<Double> withoutOutlier = new ArrayList<>(days);
        withoutOutlier.add(0.0300);

        assertThat(ObservedBeta.of(withOutlier)).isEqualTo(new ObservedBeta(new BigDecimal("0.030000"), 10));
        assertThat(ObservedBeta.of(withoutOutlier)).isEqualTo(ObservedBeta.of(withOutlier));
    }
}
