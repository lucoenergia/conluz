# Spec — Community production reads

| | |
| --- | --- |
| **Prefix** | `CPR` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → operations `getInstantProduction`, `getHourlyProduction`, `getDailyProduction`, `getMonthlyProduction`, `getYearlyProduction`, `getDatadisHourlyProduction`, `getDatadisDailyProduction`, `getDatadisMonthlyProduction`, `getDatadisYearlyProduction` |
| **Related ADRs** | — |
| **Last reviewed against code** | #552 |

## Purpose

The community production reads report the production of the plants of a community, from Huawei
(`getInstantProduction` and the hourly, daily, monthly and yearly series) or derived from Datadis
(the `getDatadis*Production` series). This spec covers which supply they accept as `supplyId`. It
covers only the rule #552 touched.

## Glossary

| Term | Meaning |
| --- | --- |
| Backing supply of a plant | The supply a plant is attached to (PLT-002). |
| Consumer supply | A supply of the community that backs no plant. |
| Partition coefficient | The share of a plant's production a sharing agreement assigns to a supply. |

## Rules

### CPR-001 — `supplyId` is accepted only when it backs a plant of the community

- **Status:** Active
- **Rule:** A community production read that receives `supplyId` MUST accept it only when it is the
  backing supply of a plant of the community in the path, and MUST answer 404 otherwise. This
  applies to both the Huawei and the Datadis variants.
- **Rationale:** Any active member can call these reads. Accepting any community supply exposes
  other members' coefficient-weighted allocations, which `canReadSupply` protects on the
  supply-scoped path (`/api/v1/supplies/{supplyId}/production/*`). The 404 does not confirm that a
  supply the read does not cover exists.
- **Source:** #552

**Scenarios**

```gherkin
Given an active member of a community
When they read the community's production with a consumer supply of the community as supplyId, theirs or another member's
Then the response is 404

Given an active member of a community
When they read the community's production with the backing supply of any plant of the community as supplyId
Then the response is 200
```
