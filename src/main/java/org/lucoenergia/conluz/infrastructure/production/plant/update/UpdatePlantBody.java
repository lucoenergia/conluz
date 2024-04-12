package org.lucoenergia.conluz.infrastructure.production.plant.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.Plant;

import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {
        "code", "name", "address", "totalPower", "personalId", "inverterProvider"
})
public class UpdatePlantBody {

    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String personalId;
    @NotNull
    private InverterProvider inverterProvider;
    private LocalDate connectionDate;
    @NotBlank
    private String address;
    @Positive
    private Double totalPower;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public InverterProvider getInverterProvider() {
        return inverterProvider;
    }

    public void setInverterProvider(InverterProvider inverterProvider) {
        this.inverterProvider = inverterProvider;
    }

    public LocalDate getConnectionDate() {
        return connectionDate;
    }

    public void setConnectionDate(LocalDate connectionDate) {
        this.connectionDate = connectionDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getTotalPower() {
        return totalPower;
    }

    public void setTotalPower(Double totalPower) {
        this.totalPower = totalPower;
    }

    public Plant toPlant(UUID uuid) {
        Plant.Builder builder = new Plant.Builder();
        builder.withId(uuid)
                .withCode(code)
                .withName(name)
                .withDescription(description)
                .withInverterProvider(inverterProvider)
                .withConnectionDate(connectionDate)
                .withAddress(address)
                .withTotalPower(totalPower)
                .withUser(new User.Builder().personalId(personalId).build());
        return builder.build();
    }
}
