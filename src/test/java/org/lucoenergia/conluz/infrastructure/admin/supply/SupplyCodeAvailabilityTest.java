package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeAvailability;
import org.lucoenergia.conluz.domain.admin.supply.SupplyRegisteredInAnotherCommunityException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplyCodeAvailabilityTest {

    private static final SupplyCode CODE = SupplyCode.of("ES0031300325733001FH");
    private static final List<String> FORMS = List.of("ES0031300325733001FH0F", "ES0031300325733001FH");
    private static final UUID COMMUNITY = UUID.randomUUID();
    private static final UUID OTHER_COMMUNITY = UUID.randomUUID();
    private static final SupplyId SUPPLY = SupplyId.of(UUID.randomUUID());

    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final SupplyCodeAvailability availability = new SupplyCodeAvailabilityImpl(getSupplyRepository);

    @Test
    @DisplayName("SUP-005 a CUPS no supply has is available")
    void aCupsNoSupplyHas_isAvailable() {
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS)).thenReturn(Set.of());

        assertThatCode(() -> availability.requireAvailable(CODE, COMMUNITY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUP-005 a CUPS a supply of the community has already exists, even if other communities have it too")
    void aCupsOfTheCommunity_alreadyExists() {
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS))
                .thenReturn(Set.of(OTHER_COMMUNITY, COMMUNITY));

        assertThatThrownBy(() -> availability.requireAvailable(CODE, COMMUNITY))
                .isInstanceOf(SupplyAlreadyExistsException.class);
    }

    @Test
    @DisplayName("SUP-005 a CUPS only supplies of other communities have is registered in another community")
    void aCupsOfOtherCommunitiesOnly_isRegisteredInAnotherCommunity() {
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS)).thenReturn(Set.of(OTHER_COMMUNITY));

        assertThatThrownBy(() -> availability.requireAvailable(CODE, COMMUNITY))
                .isInstanceOf(SupplyRegisteredInAnotherCommunityException.class);
    }

    @Test
    @DisplayName("SUP-005 for a code change, the supply itself is left out of the comparison")
    void aCodeChange_leavesTheSupplyOut() {
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS)).thenReturn(Set.of(COMMUNITY));
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS, SUPPLY)).thenReturn(Set.of());

        assertThatCode(() -> availability.requireAvailable(CODE, COMMUNITY, SUPPLY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUP-005 for a code change, another supply of the community with the CUPS refuses it")
    void aCodeChange_toAnotherSupplysCups_alreadyExists() {
        when(getSupplyRepository.findCommunityIdsByUpperCaseCodes(FORMS, SUPPLY)).thenReturn(Set.of(COMMUNITY));

        assertThatThrownBy(() -> availability.requireAvailable(CODE, COMMUNITY, SUPPLY))
                .isInstanceOf(SupplyAlreadyExistsException.class);
    }
}
