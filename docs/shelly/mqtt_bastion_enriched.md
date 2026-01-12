# Raspberry Pi as an MQTT Bastion Broker (Enriched Version)

## Why disable non-essential services?
Non-essential services consume resources and can expose additional network attack surfaces. For example:
- `avahi-daemon` exposes mDNS service used for auto-discovery.
- `cups` exposes printing services rarely needed on a bastion.
- `bluetooth` enables radio interface not required in headless IoT deployments.

Disabling reduces:
- CPU/RAM footprint
- available attack vectors
- unexpected open ports

## Monitoring & Alerting
To ensure reliable operation, monitor:
- MQTT client count
- MQTT message rate
- authentication failures
- disconnections
- broker uptime

Alerts can be generated via Telegraf + InfluxDB + Grafana.

## SVG Diagram
```svg
<svg width="300" height="120">
<rect x="10" y="20" width="130" height="50" fill="lightblue" stroke="black"/>
<text x="20" y="50">Remote Shelly</text>
<rect x="160" y="20" width="130" height="50" fill="orange" stroke="black"/>
<text x="170" y="50">MQTT Bastion</text>
</svg>
```

