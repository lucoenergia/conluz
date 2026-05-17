package org.lucoenergia.conluz.infrastructure.admin.supply.shelly;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;

public class SupplyShellyResponse {

    @Schema(description = "MAC address of the Shelly", example = "24:4c:ab:41:99:f6")
    private final String macAddress;
    @Schema(description = "Unique identifier of the Shelly", example = "shellyem-244CAB4199F6")
    private final String id;
    @Schema(description = "MQTT prefix for the Shelly", example = "70u590f396zbae/johndoe")
    private final String mqttPrefix;

    public SupplyShellyResponse(SupplyShelly shelly) {
        this.macAddress = shelly.getMacAddress();
        this.id = shelly.getId();
        this.mqttPrefix = shelly.getMqttPrefix();
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getId() {
        return id;
    }

    public String getMqttPrefix() {
        return mqttPrefix;
    }
}
