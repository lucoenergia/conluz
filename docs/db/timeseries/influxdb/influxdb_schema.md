# InfluxDB Schema

The time-series database contains the following measurements:

- **`datadis_consumption_kwh`**: Consumption data from Datadis
  - Fields: `consumption_kwh`, `generation_energy_kwh`, `obtain_method`, `self_consumption_energy_kwh`, `surplus_energy_kwh`
  - Tags: `cups` (supply point identifier)

- **`huawei_production_hourly`**: Hourly production data from Huawei inverters
  - Fields: `inverter_power`, `ongrid_power`, `power_profit`, `radiation_intensity`, `theory_power`
  - Tags: `station_code` (plant identifier)

- **`huawei_production_realtime`**: Real-time production snapshots from Huawei inverters
  - Fields: `day_income`, `day_power`, `month_power`, `real_health_state`, `total_income`, `total_power`
  - Tags: `station_code` (plant identifier)

- **`omie_prices_kwh`**: Electricity market prices from OMIE
  - Fields: `price1` (price in €/kWh)
  - Tags: none

- **`shelly_consumption_kw`**: Consumption data from Shelly devices
  - Fields: `consumption_kw`
  - Tags: `channel`, `prefix` (device identifier)

- **`shelly_mqtt_power_messages`**: Raw MQTT power messages from Shelly devices
  - Fields: `value` (power in watts)
  - Tags: `host`, `topic` (MQTT topic path)

For the complete schema with sample data, see `docs/db/timeseries/influxdb/influxdb_schema.txt`.
