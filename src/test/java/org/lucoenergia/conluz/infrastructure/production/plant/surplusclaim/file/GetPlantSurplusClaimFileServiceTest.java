package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimMonthNotClosedException;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileFormat;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPlantSurplusClaimFileServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    private final GetPlantSurplusClaimService preview = mock(GetPlantSurplusClaimService.class);
    private final SurplusClaimFileFormat endesa = format(SurplusClaimFileType.ENDESA);
    private final SurplusClaimFileFormat generic = format(SurplusClaimFileType.GENERIC);

    @Test
    @DisplayName("SCL-014 the requested format writes the preview's claim of the requested month, read once")
    void theRequestedFormatWritesThePreviewsClaim() {
        PlantSurplusClaim claim = SurplusClaimFileTestClaims.month(MONTH).build();
        SurplusClaimFile written = new SurplusClaimFile("excedentes.csv", "text/csv", new byte[]{1});
        when(preview.getSurplusClaim(PLANT_ID, MONTH)).thenReturn(claim);
        when(generic.write(claim)).thenReturn(written);

        SurplusClaimFile file = new GetPlantSurplusClaimFileServiceImpl(preview, List.of(endesa, generic))
                .getSurplusClaimFile(PLANT_ID, MONTH, SurplusClaimFileType.GENERIC);

        assertSame(written, file);
        verify(preview, times(1)).getSurplusClaim(PLANT_ID, MONTH);
        verify(endesa, never()).write(any());
    }

    @Test
    @DisplayName("SCL-014 a month that is not closed is refused as the preview refuses it, before any format writes")
    void aMonthThatIsNotClosedIsRefused() {
        when(preview.getSurplusClaim(PLANT_ID, MONTH))
                .thenThrow(new SurplusClaimMonthNotClosedException(MONTH, MONTH));
        GetPlantSurplusClaimFileServiceImpl service =
                new GetPlantSurplusClaimFileServiceImpl(preview, List.of(endesa, generic));

        assertThrows(SurplusClaimMonthNotClosedException.class,
                () -> service.getSurplusClaimFile(PLANT_ID, MONTH, SurplusClaimFileType.ENDESA));
        verify(endesa, never()).write(any());
    }

    @Test
    @DisplayName("SCL-015 a file without a month is refused: the preview's default month never applies to it")
    void aFileWithoutAMonthIsRefused() {
        GetPlantSurplusClaimFileServiceImpl service =
                new GetPlantSurplusClaimFileServiceImpl(preview, List.of(endesa, generic));

        assertThrows(NullPointerException.class,
                () -> service.getSurplusClaimFile(PLANT_ID, null, SurplusClaimFileType.GENERIC));
        verify(preview, never()).getSurplusClaim(any(), any());
    }

    @Test
    @DisplayName("every format type has exactly one implementation, or the service does not start")
    void everyTypeHasExactlyOneFormat() {
        assertThrows(IllegalStateException.class,
                () -> new GetPlantSurplusClaimFileServiceImpl(preview, List.of(endesa)));
        assertThrows(IllegalStateException.class, () -> new GetPlantSurplusClaimFileServiceImpl(preview,
                List.of(endesa, generic, format(SurplusClaimFileType.GENERIC))));
    }

    private static SurplusClaimFileFormat format(SurplusClaimFileType type) {
        SurplusClaimFileFormat format = mock(SurplusClaimFileFormat.class);
        when(format.type()).thenReturn(type);
        return format;
    }
}
