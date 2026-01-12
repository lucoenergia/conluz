# Raspberry Pi as an MQTT Bastion Broker for Remote Shelly Devices
_Last updated: April 2026_<br>
_Level: Intermediate_<br>
_Audience: Users with basic Linux + IoT experience_<br>
_Use Case: Remote Shelly 1EM devices publishing plain MQTT over the Internet_

---

## 1. Introduction

Shelly 1EM and similar first‑generation Shelly devices publish telemetry via plain MQTT (port **1883/TCP**).  
These devices **do not support TLS** nor VPN and therefore cannot safely expose data directly to a LAN or internal servers such as InfluxDB or Home Assistant.

This guide describes how to deploy a **Raspberry Pi 3 Model B** as an **MQTT Bastion Broker**.  
The bastion acts as a **DMZ-like bridge** between remote Shelly devices on the Internet and internal analytics systems inside the LAN.

A second device inside the LAN runs:
- **Telegraf** (MQTT consumer)
- **InfluxDB**
- **Grafana**

Telegraf **pulls** data from the bastion broker. The bastion itself **cannot connect back** into the LAN, improving security and preventing lateral movement if the bastion is compromised.

---

## 2. Architecture Overview

### Logical Diagram

```
+--------------+        Internet        +-----------------+
|  Shelly 1EM  |  MQTT (1883/TCP, WAN)  |   Pi Bastion     |
| (Remote)     +----------------------->|   Mosquitto      |
+--------------+                        |  ACL + Firewall  |
                                         +--------+--------+
                                                  |
                                                  | MQTT (Pull)
                                                  | (LAN only)
                                        +---------v---------+
                                        |     Telegraf      |
                                        | InfluxDB + Grafana|
                                        +-------------------+
```

### Design Goals

✔ No direct exposure of LAN systems to the Internet  
✔ No MQTT plaintext inside LAN (except local hop)  
✔ Bastion cannot pivot into LAN  
✔ Internal systems **pull** from DMZ  
✔ Scales to **hundreds of Shelly devices**  

---

## 3. Hardware and System Requirements

### Raspberry Pi Bastion

- Raspberry Pi **3 Model B**
- microSD card (≥ 16 GB recommended)
- Stable 5V power supply
- Ethernet connectivity (recommended)

Why Pi 3 is sufficient:

- Mosquitto uses ~20–60 MB RAM under load
- 300+ MQTT clients at 60s reporting interval yield < 10 msg/s
- CPU and NIC are more than sufficient

### Internal LAN Server

Runs one or more of:

- Telegraf
- InfluxDB
- Grafana

Telegraf connects via MQTT to the Pi.

### Network Requirements

- Public IP (dynamic or static)
- Router capable of **port forwarding (NAT)**
- LAN subnet (example: `192.168.1.0/24`)
- No need for VLANs or router replacement

---

## 4. Preparing the Raspberry Pi

Install Raspberry Pi OS Lite (or similar).

Update system:

```bash
sudo apt update && sudo apt upgrade -y
```

Disable non-essential services:

```bash
sudo systemctl disable --now avahi-daemon
sudo systemctl disable --now cups
sudo systemctl disable --now bluetooth
```

---

## 5. Installing Mosquitto Broker

Install package:

```bash
sudo apt install mosquitto mosquitto-clients -y
```

Create config `/etc/mosquitto/mosquitto.conf`:

```
listener 1883
allow_anonymous false
password_file /etc/mosquitto/passwd
acl_file /etc/mosquitto/acl
persistence true
persistence_location /var/lib/mosquitto/
log_dest file /var/log/mosquitto/mqtt.log
```

---

## 6. MQTT Users and ACL Configuration

Create MQTT user for remote Shellys:

```bash
sudo mosquitto_passwd -c /etc/mosquitto/passwd shelly
```

Create ACL file `/etc/mosquitto/acl`:

```
user shelly
topic write shelly/#

user telegraf_reader
topic read shelly/#
```

Restart:

```bash
sudo systemctl restart mosquitto
```

### Why ACL matters

ACL prevents:

- remote spoofing
- topic hijacking
- eavesdropping between devices
- unintended subscriptions

---

## 7. Network Isolation of the Bastion (Critical Security Step)

Install UFW:

```bash
sudo apt install ufw
```

Restrictive defaults:

```bash
sudo ufw default deny incoming
sudo ufw default deny outgoing
```

Allow incoming MQTT from Internet:

```bash
sudo ufw allow 1883/tcp
```

Allow DNS + OS updates:

```bash
sudo ufw allow out 53
sudo ufw allow out 80
sudo ufw allow out 443
```

Block outbound access to LAN:

```bash
sudo ufw deny out to 192.168.1.0/24
sudo ufw deny out to 10.0.0.0/8
sudo ufw deny out to 172.16.0.0/12
```

Enable firewall:

```bash
sudo ufw enable
```

### Result

```
WAN → Bastion → MQTT allowed
LAN → Bastion → MQTT allowed (pull)
Bastion → LAN → BLOCKED
```

This prevents lateral movement if the bastion is compromised.

---

## 8. Router Configuration (Port Forwarding)

Forward external 1883/TCP to the Raspberry Pi:

```
External Port: 1883 (TCP)
Internal Port: 1883 (TCP)
Destination: RPi_Bastion_IP
```

### Verification from mobile (4G)

```
telnet <public_ip> 1883
```

Should open a TCP socket.

---

## 9. Shelly 1EM Remote Configuration

On each Shelly:

```
MQTT Server: <public_domain_or_ip>:1883
User: shelly
Password: <password>
Update period: ~60s (recommended for large fleets)
```

Shelly publishes topics such as:

```
shelly/<id>/emeter/0/power
shelly/<id>/emeter/0/voltage
shelly/<id>/emeter/0/energy
```

---

## 10. Pulling MQTT from LAN Using Telegraf

Example `telegraf.conf`:

```toml
[[inputs.mqtt_consumer]]
  servers = ["tcp://192.168.1.200:1883"]
  topics = ["shelly/#"]
  username = "telegraf_reader"
  password = "xxxx"
  data_format = "json"

[[outputs.influxdb_v2]]
  urls = ["http://127.0.0.1:8086"]
  token = "xxxxx"
  bucket = "energy"
  organization = "myorg"
```

### Why pull instead of push

Pull avoids:

- outbound LAN access from DMZ
- NAT mapping issues
- unsolicited inbound LAN traffic

---

## 11. Persisting to InfluxDB and Visualizing with Grafana

Once Telegraf writes Shelly data into InfluxDB:

- Grafana can plot power, voltage and energy trends
- multi-site dashboards become trivial
- time aggregation is straightforward (1m, 5m, 1h)

Common panels:

- import/export power
- voltage stability
- cumulative energy
- comparisons between sites/customers

---

## 12. Security Considerations

Summary of threats mitigated:

✔ LAN pivoting  
✔ credential sniffing  
✔ topic spoofing  
✔ brute subscription  
✔ access to NAS/Home Assistant  
✔ data exfiltration from LAN  

If the bastion is compromised, attacker gains at most:

- access to MQTT feed
- ability to publish false data
- DoS capability

But cannot:

✖ scan LAN  
✖ attack internal services  
✖ exfiltrate LAN secrets  

---

## 13. Testing and Validation

### Test 1 — Remote connectivity

```
mosquitto_pub -h <public_ip> -p 1883 -u shelly -P xxx -t test -m hello
```

### Test 2 — LAN Telegraf read

```
mosquitto_sub -h 192.168.1.200 -t shelly/# -u telegraf_reader -P xxx
```

### Test 3 — LAN isolation

From Pi:

```
nmap 192.168.1.0/24
```

Expected: `filtered/closed`

---

## 14. Troubleshooting

| Issue | Likely Cause |
|---|---|
| Telegraf not receiving | ACL mismatch or firewall |
| Shelly disconnects | Update period too low |
| No WAN connectivity | NAT misconfigured |
| Pi accesses LAN | UFW rules incomplete |
| Slow data | Poor Shelly Internet uplink |

---

## 15. Long-term Maintenance

Recommended:

- rotate MQTT passwords
- monitor connection counts
- inspect Mosquitto logs
- back up ACL and config files
- update Raspberry Pi periodically

---

## 16. Conclusion

This pattern enables secure ingestion of telemetry from remote Shelly devices using plain MQTT without exposing internal analytics systems to the Internet.  
The bastion approach is lightweight, scalable and aligns with principles used in industrial and OT/IT architectures.

---

## 17. Glossary

**Bastion** — Isolated node used to absorb external traffic  
**DMZ** — Network zone between WAN and internal LAN  
**MQTT** — Publish/subscribe protocol for IoT devices  
**ACL** — Access Control List (fine-grained permissions)  
**Pull model** — LAN initiates connection to DMZ  
**Shelly** — IoT energy measurement device

