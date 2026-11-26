package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.GetPlantSurplusClaimFileService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileFormat;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads the month once through {@link GetPlantSurplusClaimService}, the preview's own computation, and hands it to
 * the requested format. It costs what the preview costs.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantSurplusClaimFileServiceImpl implements GetPlantSurplusClaimFileService {

    private final GetPlantSurplusClaimService getPlantSurplusClaimService;
    private final Map<SurplusClaimFileType, SurplusClaimFileFormat> formats = new EnumMap<>(SurplusClaimFileType.class);

    public GetPlantSurplusClaimFileServiceImpl(GetPlantSurplusClaimService getPlantSurplusClaimService,
                                               List<SurplusClaimFileFormat> formats) {
        this.getPlantSurplusClaimService = getPlantSurplusClaimService;
        for (SurplusClaimFileFormat format : formats) {
            if (this.formats.put(format.type(), format) != null) {
                throw new IllegalStateException("Two surplus claim file formats write " + format.type());
            }
        }
        List<SurplusClaimFileType> missing = Arrays.stream(SurplusClaimFileType.values())
                .filter(type -> !this.formats.containsKey(type))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No surplus claim file format writes " + missing);
        }
    }

    @Override
    public SurplusClaimFile getSurplusClaimFile(UUID plantId, YearMonth month, SurplusClaimFileType type) {
        Objects.requireNonNull(month, "month");
        PlantSurplusClaim claim = getPlantSurplusClaimService.getSurplusClaim(plantId, month);
        return formats.get(type).write(claim);
    }
}
