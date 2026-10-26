package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.production.huawei.config.LockHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfiguration;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetHuaweiConfigurationServiceTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String BASE_URL = "https://fusionsolar.example.com/thirdData";

    @Mock
    private LockPlantRepository lockPlantRepository;
    @Mock
    private LockHuaweiConfigRepository lockHuaweiConfigRepository;
    @Mock
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Mock
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    private SetHuaweiConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new SetHuaweiConfigurationServiceImpl(lockPlantRepository, lockHuaweiConfigRepository,
                getHuaweiConfigRepository, setHuaweiConfigurationRepository);
        lenient().when(lockPlantRepository.lockAsReferenced(PlantId.of(PLANT_ID))).thenReturn(true);
    }

    @Test
    @DisplayName("HCF-001 without a password the stored non-empty password is written back with the other fields")
    void keepsStoredPassword() {
        givenStored(storedConfig("stored"));

        service.setHuaweiConfiguration(PLANT_ID, configuration());

        HuaweiConfig written = written();
        assertThat(written.getPassword()).isEqualTo("stored");
        assertThat(written.getUsername()).isEqualTo("newUser");
        assertThat(written.getBaseUrl()).isEqualTo(BASE_URL);
    }

    @Test
    @DisplayName("a supplied password is written as given")
    void writesSuppliedPassword() {
        service.setHuaweiConfiguration(PLANT_ID, configuration("newPassword"));

        assertThat(written().getPassword()).isEqualTo("newPassword");
    }

    @Test
    @DisplayName("HCF-002 without a password and without a configuration nothing is written")
    void rejectsMissingPasswordWithoutConfig() {
        when(lockHuaweiConfigRepository.lock(PLANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.setHuaweiConfiguration(PLANT_ID, configuration()))
                .isInstanceOf(HuaweiConfigPasswordRequiredException.class);
        verify(setHuaweiConfigurationRepository, never()).setHuaweiConfiguration(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("HCF-002 without a password and with a stored null or empty password nothing is written")
    void rejectsMissingPasswordOverStoredBlankPassword(String storedPassword) {
        givenStored(storedConfig(storedPassword));

        assertThatThrownBy(() -> service.setHuaweiConfiguration(PLANT_ID, configuration()))
                .isInstanceOf(HuaweiConfigPasswordRequiredException.class);
        verify(setHuaweiConfigurationRepository, never()).setHuaweiConfiguration(any(), any());
    }

    @Test
    @DisplayName("HCF-006 without a stored configuration the write stores it disabled")
    void newConfigurationIsWrittenDisabled() {
        when(lockHuaweiConfigRepository.lock(PLANT_ID)).thenReturn(false);

        service.setHuaweiConfiguration(PLANT_ID, configuration("newPassword"));

        assertThat(written().getEnabled()).isFalse();
        verify(getHuaweiConfigRepository, never()).getHuaweiConfig(any());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("HCF-006 with a stored configuration the write keeps its stored enabled")
    void storedEnabledIsWrittenBack(boolean storedEnabled, boolean passwordSupplied) {
        givenStored(storedConfig("stored", storedEnabled));

        service.setHuaweiConfiguration(PLANT_ID, passwordSupplied ? configuration("newPassword") : configuration());

        assertThat(written().getEnabled()).isEqualTo(storedEnabled);
    }

    @Test
    @DisplayName("HCF-006 the configuration is locked before it is read, so a concurrent enable or disable is not reverted")
    void locksBeforeReading() {
        givenStored(storedConfig("stored"));

        service.setHuaweiConfiguration(PLANT_ID, configuration());

        InOrder order = inOrder(lockHuaweiConfigRepository, getHuaweiConfigRepository, setHuaweiConfigurationRepository);
        order.verify(lockHuaweiConfigRepository).lock(PLANT_ID);
        order.verify(getHuaweiConfigRepository).getHuaweiConfig(PLANT_ID);
        order.verify(setHuaweiConfigurationRepository).setHuaweiConfiguration(eq(PLANT_ID), any());
    }

    @Test
    @DisplayName("PLT-001 the plant is locked against deletion before the configuration row, in a plant deletion's order")
    void locksThePlantBeforeTheConfiguration() {
        givenStored(storedConfig("stored"));

        service.setHuaweiConfiguration(PLANT_ID, configuration());

        InOrder order = inOrder(lockPlantRepository, lockHuaweiConfigRepository, setHuaweiConfigurationRepository);
        order.verify(lockPlantRepository).lockAsReferenced(PlantId.of(PLANT_ID));
        order.verify(lockHuaweiConfigRepository).lock(PLANT_ID);
        order.verify(setHuaweiConfigurationRepository).setHuaweiConfiguration(eq(PLANT_ID), any());
    }

    @Test
    @DisplayName("PLT-001 a plant deleted before the write is not found, and nothing is written")
    void aDeletedPlantIsNotFound() {
        when(lockPlantRepository.lockAsReferenced(PlantId.of(PLANT_ID))).thenReturn(false);

        assertThatThrownBy(() -> service.setHuaweiConfiguration(PLANT_ID, configuration("newPassword")))
                .isInstanceOf(PlantNotFoundException.class);
        verifyNoInteractions(lockHuaweiConfigRepository, getHuaweiConfigRepository, setHuaweiConfigurationRepository);
    }

    private static SetHuaweiConfiguration configuration() {
        return builder().build();
    }

    private static SetHuaweiConfiguration configuration(String password) {
        return builder().withPassword(password).build();
    }

    private static SetHuaweiConfiguration.Builder builder() {
        return new SetHuaweiConfiguration.Builder()
                .withUsername("newUser")
                .withBaseUrl(BASE_URL);
    }

    private static HuaweiConfig storedConfig(String password) {
        return storedConfig(password, true);
    }

    private static HuaweiConfig storedConfig(String password, boolean enabled) {
        return new HuaweiConfig.Builder()
                .setUsername("storedUser")
                .setPassword(password)
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .setPlantId(PLANT_ID)
                .build();
    }

    private void givenStored(HuaweiConfig stored) {
        when(lockHuaweiConfigRepository.lock(PLANT_ID)).thenReturn(true);
        when(getHuaweiConfigRepository.getHuaweiConfig(PLANT_ID)).thenReturn(Optional.of(stored));
    }

    private HuaweiConfig written() {
        ArgumentCaptor<HuaweiConfig> captor = ArgumentCaptor.forClass(HuaweiConfig.class);
        verify(setHuaweiConfigurationRepository).setHuaweiConfiguration(eq(PLANT_ID), captor.capture());
        return captor.getValue();
    }
}
