package org.lucoenergia.conluz.infrastructure.admin.community.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetAllCommunitiesController {

    private final GetCommunityService service;
    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final SupplyRepository supplyRepository;

    public GetAllCommunitiesController(GetCommunityService service,
                                       CommunityMembershipJpaRepository membershipJpaRepository,
                                       SupplyRepository supplyRepository) {
        this.service = service;
        this.membershipJpaRepository = membershipJpaRepository;
        this.supplyRepository = supplyRepository;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves all communities visible to the current user.",
            description = """
                    Returns the list of communities the current user is allowed to see.
                    Platform admins see all communities. Regular users only see communities they belong to.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getAllCommunities",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Communities retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("isAuthenticated()")
    public List<CommunityResponse> getAllCommunities() {
        List<Community> communities = service.findAllVisible();

        if (communities.isEmpty()) {
            return List.of();
        }

        Set<UUID> communityIds = communities.stream()
                .map(Community::getId)
                .collect(Collectors.toSet());

        Map<UUID, Integer> memberCounts = buildMemberCountMap(communityIds);
        Map<UUID, Integer> supplyPointCounts = buildSupplyPointCountMap(communityIds);
        Map<UUID, List<String>> adminNames = buildAdminNamesMap(communityIds);

        return communities.stream()
                .map(c -> new CommunityResponse(
                        c,
                        adminNames.getOrDefault(c.getId(), List.of()),
                        memberCounts.getOrDefault(c.getId(), 0),
                        supplyPointCounts.getOrDefault(c.getId(), 0)))
                .toList();
    }

    private Map<UUID, Integer> buildMemberCountMap(Set<UUID> communityIds) {
        List<Object[]> rows = membershipJpaRepository.countMembersByCommunityIds(communityIds);
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private Map<UUID, Integer> buildSupplyPointCountMap(Set<UUID> communityIds) {
        List<Object[]> rows = supplyRepository.countSuppliesByCommunityIds(communityIds);
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private Map<UUID, List<String>> buildAdminNamesMap(Set<UUID> communityIds) {
        List<Object[]> rows = membershipJpaRepository.findAdminNamesByCommunityIds(communityIds);
        Map<UUID, List<String>> map = new HashMap<>();
        for (Object[] row : rows) {
            UUID communityId = (UUID) row[0];
            String adminName = (String) row[1];
            map.computeIfAbsent(communityId, k -> new ArrayList<>()).add(adminName);
        }
        return map;
    }
}
