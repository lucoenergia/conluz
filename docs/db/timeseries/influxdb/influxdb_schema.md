# InfluxDB Schema

The time-series database contains the following measurements:

- **`datadis_consumption_kwh`**: Consumption data from Datadis
  - Fields: `consumption_kwh`, `generation_energy_kwh`, `obtain_method`, `self_consumption_energy_kwh`, `surplus_energy_kwh`
  - Tags: `cups` (supply point identifier)

- **`datadis_consumption_kwh_month`**: Monthly consumption per supply, aggregated from `datadis_consumption_kwh`
  - Fields: `consumption_kwh`, `generation_energy_kwh`, `self_consumption_energy_kwh`, `surplus_energy_kwh` (sums over the month), `obtain_method` (the month's last value)
  - Tags: `cups` (supply point identifier)
  - Point time: the start of the month in the application's zone (`conluz.time.zone.id`)

- **`datadis_consumption_kwh_year`**: Yearly consumption per supply, aggregated from `datadis_consumption_kwh_month`
  - Fields: `consumption_kwh`, `generation_energy_kwh`, `self_consumption_energy_kwh`, `surplus_energy_kwh` (sums over the year), `obtain_method` (the year's last value)
  - Tags: `cups` (supply point identifier)
  - Point time: the start of the year in the application's zone (`conluz.time.zone.id`)

- **`datadis_production_kwh`**: Hourly production data from Datadis
  - Fields: `obtain_method`, `production_kwh`
  - Tags: `cups` (supply point identifier)
  - Point time: the date and time Datadis reports for the reading, read in the application's zone (`conluz.time.zone.id`)

- **`datadis_production_kwh_month`**: Monthly production per supply, aggregated from `datadis_production_kwh`
  - Fields: `production_kwh` (sum over the month), `obtain_method` (the month's last value)
  - Tags: `cups` (supply point identifier)
  - Point time: the start of the month in the application's zone (`conluz.time.zone.id`)

- **`datadis_production_kwh_year`**: Yearly production per supply, aggregated from `datadis_production_kwh_month`
  - Fields: `production_kwh` (sum over the year), `obtain_method` (the year's last value)
  - Tags: `cups` (supply point identifier)
  - Point time: the start of the year in the application's zone (`conluz.time.zone.id`)

- **`huawei_production_hourly`**: Hourly production data from Huawei inverters
  - Fields: `inverter_power`, `ongrid_power`, `power_profit`, `radiation_intensity`, `theory_power`
  - Tags: `station_code` (plant identifier)

- **`huawei_production_kwh_month`**: Monthly production per plant, aggregated from `huawei_production_hourly`
  - Fields: `inverter_power`, `ongrid_power`, `power_profit`, `radiation_intensity`, `theory_power` (sums over the month)
  - Tags: `station_code` (plant identifier)
  - Point time: the start of the month in the application's zone (`conluz.time.zone.id`)

- **`huawei_production_kwh_year`**: Yearly production per plant, aggregated from `huawei_production_kwh_month`
  - Fields: `inverter_power`, `ongrid_power`, `power_profit`, `radiation_intensity`, `theory_power` (sums over the year)
  - Tags: `station_code` (plant identifier)
  - Point time: the start of the year in the application's zone (`conluz.time.zone.id`)

- **`huawei_production_realtime`**: Real-time production snapshots from Huawei inverters
  - Fields: `day_income`, `day_power`, `month_power`, `real_health_state`, `total_income`, `total_power`
  - Tags: `station_code` (plant identifier)

- **`omie_prices_kwh`**: Electricity market prices from OMIE
  - Fields: `price1` (price in €/kWh)
  - Tags: none

- **`shelly_consumption_kw`**: Consumption data from Shelly devices
  - Fields: `consumption_kw`
  - Tags: `channel`, `prefix` (device identifier)

- **`shelly_consumption_kwh`**: Hourly consumption per supply, aggregated from `shelly_consumption_kw`
  - Fields: `consumption_kwh` (the integral of `consumption_kw` over the hour)
  - Tags: `prefix` (device identifier, the supply's Shelly MQTT prefix)
  - Point time: the start of the hour

- **`shelly_mqtt_power_messages`**: Raw MQTT power messages from Shelly devices
  - Fields: `value` (power in watts)
  - Tags: `host`, `topic` (MQTT topic path)

For the complete schema with sample data, see `docs/db/timeseries/influxdb/influxdb_schema.txt`.
