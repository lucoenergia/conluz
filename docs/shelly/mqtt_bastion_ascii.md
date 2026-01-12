# Raspberry Pi as an MQTT Bastion Broker (ASCII Visual Edition)

## Architecture (ASCII)

WAN Shelly Fleet -> Bastion -> Telegraf -> InfluxDB -> Grafana

 +-------------+        +-----------------+        +-----------+
 | Shelly 1EM  |  MQTT  | MQTT Bastion    |  Pull  | Telegraf  |
 |  (remote)   +------->| (Mosquitto)     +------->| (LAN)     |
 +-------------+        | ACL + Firewall  |        +-----------+
                        +-----------------+               |
                                                         Write
                                                          |
                                                     +-----------+
                                                     | InfluxDB  |
                                                     +-----------+
                                                          |
                                                      Query/Vis
                                                          |
                                                     +-----------+
                                                     | Grafana   |
                                                     +-----------+

## Data Flow (ASCII sequence)

 Shelly ---> Bastion ---> Telegraf ---> InfluxDB ---> Grafana
   PUB         STORE        SUB          WRITE          READ

## Why ASCII visuals?
- Portable
- Render in terminals, wikis, PDFs
- No dependencies or plugins
