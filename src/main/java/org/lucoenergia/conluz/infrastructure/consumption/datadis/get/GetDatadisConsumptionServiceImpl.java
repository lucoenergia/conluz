package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetDatadisConsumptionServiceImpl implements GetDatadisConsumptionService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final AuthService authService;

    public GetDatadisConsumptionServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository,
            AuthService authService) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.authService = authService;
    }

    @Override
    public List<DatadisConsumption> getDailyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                OffsetDateTime endDate) {
        // Get current authenticated user
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));

        // Get the supply
        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }

        Supply supply = supplyOptional.get();

        // Authorization: ADMIN can access any supply, non-ADMIN can only access their own supplies
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = supply.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("User does not have permission to access this supply's consumption data");
        }

        // Retrieve consumption data
        return getDatadisConsumptionRepository.getDailyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        // Get current authenticated user
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));

        // Get the supply
        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }

        Supply supply = supplyOptional.get();

        // Authorization: ADMIN can access any supply, non-ADMIN can only access their own supplies
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = supply.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("User does not have permission to access this supply's consumption data");
        }

        // Retrieve consumption data
        return getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }
}
