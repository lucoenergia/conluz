package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesOutcome;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesPlan;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreviewCoefficientDatesServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_ID = UUID.randomUUID();
    private static final CoefficientDatesRequest REQUEST = new CoefficientDatesRequest(List.of(), List.of());

    @Mock
    private CoefficientDatesPlanner planner;

    @Test
    @DisplayName("SHA-014 the preview returns the planned effects, and only plans: it neither locks nor writes")
    void returnsThePlannedEffectsWithoutWriting() {
        CoefficientDatesEffects effects = new CoefficientDatesEffects(List.of(), List.of(), List.of(), List.of(),
                new CoefficientDatesEffects.CommunityStartChange(null, null));
        CoefficientDatesPlanner.Prepared prepared = new CoefficientDatesPlanner.Prepared(
                new CoefficientDatesPlan.Planned(List.of(), effects), UUID.randomUUID(), Map.of());
        CoefficientDatesOutcome outcome = new CoefficientDatesOutcome(effects, Map.of(), Map.of());
        when(planner.plan(PLANT_ID, AGREEMENT_ID, REQUEST)).thenReturn(prepared);
        when(planner.outcome(effects, Map.of())).thenReturn(outcome);

        // The service is built over the planner alone: it has no lock, no save and no recompute to call.
        assertThat(new PreviewCoefficientDatesServiceImpl(planner).previewDates(PLANT_ID, AGREEMENT_ID, REQUEST))
                .isSameAs(outcome);
        verify(planner).plan(PLANT_ID, AGREEMENT_ID, REQUEST);
        verify(planner).outcome(effects, Map.of());
        verifyNoMoreInteractions(planner);
    }
}
