package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sharing-agreement capabilities as a client receives them.
 *
 * <p>These are the least informative capabilities in the inventory today, and deliberately so. A
 * sharing agreement is admin-only for reads as well as writes — {@code canRead} and
 * {@code canManage} are the same rule — and every endpoint that returns one already demands it. So
 * no caller can ever observe a {@code false} here: one who would have seen it was refused the
 * response instead, with a 403 or a 404. What is worth asserting is therefore that the fields are
 * present and wired to the right agreement, not that they discriminate.</p>
 *
 * <p>They earn their place anyway: the web app reads capabilities uniformly rather than special-casing
 * this one resource, and the read rule and the write rule are kept apart precisely so they can
 * diverge later without any call site changing.</p>
 */
@Transactional
class SharingAgreementCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    private Community community;
    private Plant plant;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), community.getId());
        plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    @Test
    void theDetailEndpointCarriesCapabilities() throws Exception {
        SharingAgreementEntity agreement = persistAgreement();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get("/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}",
                        plant.getId(), agreement.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canManage").value(true));
    }

    /**
     * The listing resolves the plant once and assembles every row against it, so a second agreement
     * must come back with the same capabilities rather than with a default-constructed object.
     */
    @Test
    void theListingCarriesCapabilitiesOnEveryItem() throws Exception {
        persistAgreement();
        persistAgreement();
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get("/api/v1/plants/{plantId}/sharing-agreements", plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].capabilities.canManage").value(true))
                .andExpect(jsonPath("$[1].capabilities.canManage").value(true));
    }

    private SharingAgreementEntity persistAgreement() {
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantEntity);
        agreement.setName("Agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.DRAFT);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }
}
