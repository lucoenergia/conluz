package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionService;
import org.lucoenergia.conluz.domain.production.plant.get.CommunityPlantSupplyResolver;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class GetDatadisProductionServiceImpl implements GetDatadisProductionService {

    private final GetDatadisProductionRepository getDatadisProductionRepository;
    private final GetPlantRepository getPlantRepository;
    private final CommunityPlantSupplyResolver communityPlantSupplyResolver;

    public GetDatadisProductionServiceImpl(
            @Qualifier("getDatadisProductionRepositoryInflux") GetDatadisProductionRepository getDatadisProductionRepository,
            GetPlantRepository getPlantRepository,
            CommunityPlantSupplyResolver communityPlantSupplyResolver) {
        this.getDatadisProductionRepository = getDatadisProductionRepository;
        this.getPlantRepository = getPlantRepository;
        this.communityPlantSupplyResolver = communityPlantSupplyResolver;
    }

    @Override
    public List<DatadisProduction> getHourlyProduction(UUID communityId, SupplyId supplyId,
                                                       OffsetDateTime startDate, OffsetDateTime endDate) {
        return query(communityId, supplyId,
                cups -> getDatadisProductionRepository.getHourlyProductionByRangeOfDates(cups, startDate, endDate));
    }

    @Override
    public List<DatadisProduction> getDailyProduction(UUID communityId, SupplyId supplyId,
                                                      OffsetDateTime startDate, OffsetDateTime endDate) {
        return query(communityId, supplyId,
                cups -> getDatadisProductionRepository.getDailyProductionByRangeOfDates(cups, startDate, endDate));
    }

    @Override
    public List<DatadisProduction> getMonthlyProduction(UUID communityId, SupplyId supplyId,
                                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        return query(communityId, supplyId,
                cups -> getDatadisProductionRepository.getMonthlyProductionByRangeOfDates(cups, startDate, endDate));
    }

    @Override
    public List<DatadisProduction> getYearlyProduction(UUID communityId, SupplyId supplyId,
                                                       OffsetDateTime startDate, OffsetDateTime endDate) {
        return query(communityId, supplyId,
                cups -> getDatadisProductionRepository.getYearlyProductionByRangeOfDates(cups, startDate, endDate));
    }

    /**
     * Resolves the CUPS scope for the query and delegates to the repository. This is where the
     * per-supply ownership boundary lives (the controller only checks community membership): every
     * query is constrained to the community's plant CUPS, and an out-of-community {@code supplyId}
     * yields a 404 so it cannot be used to read another community's plant.
     */
    private List<DatadisProduction> query(UUID communityId, SupplyId supplyId,
                                          Function<Collection<String>, List<DatadisProduction>> reader) {
        if (supplyId != null) {
            String code = communityPlantSupplyResolver.resolve(communityId, supplyId).getCode();
            return reader.apply(List.of(code));
        }

        Set<String> communityCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        if (communityCups.isEmpty()) {
            return List.of();
        }
        return reader.apply(communityCups);
    }
}
