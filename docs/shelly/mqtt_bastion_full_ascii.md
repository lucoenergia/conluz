# Raspberry Pi as an MQTT Bastion Broker (Full ASCII Edition)
_Last updated: April 2026_
_Level: Intermediate_
_Use Case: Remote Shelly 1EM via WAN MQTT_

## 1. Introduction
Shelly 1EM devices publish telemetry via plain MQTT (1883/TCP) without TLS. Direct LAN exposure is unsafe.
A Raspberry Pi can serve as an MQTT Bastion Broker: isolated, ACL‑controlled, and monitored.

## 2. Architecture (ASCII)

+-------------+     MQTT WAN      +-------------------+     PULL     +-----------+
| Shelly 1EM  | --------------->  | MQTT Bastion (Pi)  | ----------> | Telegraf  |
|  (remote)   |                   | Mosquitto + ACL    |             |  (LAN)    |
+-------------+                   +-------------------+             +-----------+
                                                                WRITE     |
                                                                         v
                                                                    +-----------+
                                                                    | InfluxDB  |
                                                                    +-----------+
                                                                         |
                                                                     QUERY/VIS
                                                                         |
                                                                    +-----------+
                                                                    | Grafana   |
                                                                    +-----------+

Data Direction Summary:
  WAN PUB --> Bastion STORE --> LAN SUB --> DB WRITE --> VIS

## 3. Motivation
Main objectives:
- Contain WAN-originated MQTT traffic
- Prevent lateral movement into LAN
- Support 100+ devices securely
- Maintain analytics on LAN

## 4. Hardware Requirements
Raspberry Pi 3B sufficient:
- Mosquitto footprint < 60MB
- < 10 msgs/s typical for 300 devices
- NIC 100Mbps >> required bandwidth

## 5. OS Preparation
Disable non-essential services:
Reasoning:
- Reduce attack surface
- Remove broadcast exposure
- Lower CPU & privileged daemons

Examples:
- avahi: mDNS discovery, not needed
- cups: printing service, irrelevant
- bluetooth: unused radios

```bash
sudo systemctl disable --now avahi-daemon cups bluetooth
```

## 6. Install Mosquitto
```bash
sudo apt install mosquitto mosquitto-clients -y
```

Config `/etc/mosquitto/mosquitto.conf`:
```
listener 1883
allow_anonymous false
password_file /etc/mosquitto/passwd
acl_file /etc/mosquitto/acl
persistence true
log_dest file /var/log/mosquitto/mqtt.log
```

## 7. MQTT Credentials & ACLs
```bash
sudo mosquitto_passwd -c /etc/mosquitto/passwd shelly
sudo mosquitto_passwd /etc/mosquitto/passwd telegraf_reader
```

ACL `/etc/mosquitto/acl`:
```
user shelly
topic write shelly/#

user telegraf_reader
topic read shelly/#
```

ACL Justification:
- Prevent spoofing
- Enforce least privilege
- Avoid cross-tenant leakage
- Enable scaling

## 8. Network Isolation (Critical)
ASCII View of Allowed Flows:

             +---------------------+
             |  Internet (WAN)     |
             +---------+-----------+
                       |
                 ALLOW 1883/TCP (NAT)
                       |
               +-------v--------+
               | Bastion (Pi)   |
               | Mosq + ACL     |
               +-------+--------+
                       |
     BLOCK OUTBOUND ---+---X---> LAN
                       |
                 ALLOW PULL <---+--- Telegraf (LAN)

UFW Rules:
```bash
sudo ufw default deny incoming
sudo ufw default deny outgoing
sudo ufw allow 1883/tcp
sudo ufw allow out 53
sudo ufw allow out 80
sudo ufw allow out 443
sudo ufw deny out to 192.168.0.0/16
sudo ufw deny out to 10.0.0.0/8
sudo ufw deny out to 172.16.0.0/12
sudo ufw enable
```

Why Block Outbound?
If compromised, attacker **cannot** pivot to NAS, HA, DB, router UI, etc.

## 9. Router NAT
Forward WAN:1883/TCP -> Bastion:1883/TCP

Test from mobile:
```bash
telnet <public_ip> 1883
```

## 10. Shelly Configuration
```
Server: <public_ip>:1883
User: shelly
Password: xxxx
Update: 60s recommended
```

## 11. Telegraf Pull (LAN)
```
[[inputs.mqtt_consumer]]
 servers=["tcp://192.168.1.200:1883"]
 topics=["shelly/#"]
 username="telegraf_reader"
 password="xxxx"
 data_format="json"

[[outputs.influxdb_v2]]
 urls=["http://127.0.0.1:8086"]
 token="xxxx"
 bucket="energy"
 organization="myorg"
```

## 12. Monitoring & Alerting
KPIs:
- MQTT client count
- msgs/s rate
- auth failures
- disconnects
- CPU/RAM

Alert Triggers:
- Fleet outage (client drop)
- Burst msgs/s (DoS suspicion)
- Repeated auth failures
- WAN latency patterns

ASCII Monitoring Pipeline:
+-----------+      metrics      +-----------+      alerts     +----------+
| Bastion   | ----------------> | InfluxDB  | -------------> | Grafana  |
| Mosquitto |                   | (metrics) |                | Alerting |
+-----------+                   +-----------+                +----------+

## 13. Threat Model
Mitigated:
- WAN→LAN pivot
- Topic spoofing
- Impersonation
- Data leakage
- Multi-tenant leakage
- Cloud dependency

Residual:
- Bastion DoS (acceptable)
- Data falsification (detectable)

## 14. Validation Tests
From Pi:
```bash
nmap 192.168.1.0/24   # expect filtered/closed
```
From LAN:
```bash
mosquitto_sub -h 192.168.1.200 -t shelly/#
```

## 15. Maintenance
- Rotate creds
- Update Pi
- Check logs
- Monitor KPIs

## 16. Conclusion
The MQTT Bastion pattern isolates untrusted WAN MQTT telemetry from critical LAN analytics systems while remaining simple, scalable, and IoT‑friendly.
