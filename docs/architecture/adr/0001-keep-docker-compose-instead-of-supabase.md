# ADR-0001: Keep the Docker Compose deployment model instead of adopting Supabase

- **Status:** Accepted
- **Date:** 2026-08-22
- **Deciders:** Conluz maintainers
- **Tags:** architecture, deployment, persistence, vendor-independence

---

## Context

### What Conluz is today

Conluz is an API-driven platform for managing Spanish collective self-consumption energy
communities (*comunidades de autoconsumo colectivo*). It is published under AGPL-3.0 and is
operated as a managed service by Optimener, but it is also intended to remain genuinely
self-hostable by any community that wants to run it on its own infrastructure.

The runtime is a set of long-lived services orchestrated with Docker Compose on a single host:

| Component | Role |
|---|---|
| Spring Boot 3 / Java 17 application | Domain logic, REST API, scheduled synchronisation jobs |
| PostgreSQL + Liquibase | Relational state: users, communities, supplies, plants, sharing agreements, coefficients |
| InfluxDB 1.8 | Time series: consumption, production, instant power, aggregated totals |
| Mosquitto | MQTT broker for IoT telemetry |
| Telegraf | Ingestion pipeline from MQTT into InfluxDB |
| ChirpStack | LoRaWAN network server for the field gateways |
| Reporting service | Scheduled report rendering and email delivery |
| Observability stack | Metrics, logs and alerting |
| Reverse proxy | TLS termination and routing |

The application follows hexagonal architecture. Domain logic never depends on Spring or on web
types, and persistence is an adapter behind a port. Integration tests run the real PostgreSQL and
InfluxDB engines through Testcontainers, so the persistence adapters are verified against the same
engines used in production.

### Why this decision is being recorded now

Supabase was evaluated as a possible cloud deployment target for the Conluz backend, motivated by
the prospect of reducing operational burden through managed PostgreSQL, built-in authentication and
managed storage. This ADR records why that option was rejected and why the existing Docker Compose
model is retained.

---

## Requirements

The deployment model must satisfy the following, in decreasing order of weight:

- **R1 — Preserve hexagonal architecture.** Business rules stay in the domain layer, expressed in
  Java, testable without infrastructure.
- **R2 — Preserve self-hostability.** An energy community must be able to run the whole platform
  from the public repository, without a commercial account with any third party. This is a direct
  consequence of the AGPL licensing choice and of the value proposition offered to public-sector
  clients.
- **R3 — Run the existing Java codebase unmodified.** Migration must not require rewriting working,
  tested domain logic.
- **R4 — Support both persistence engines.** Relational state and time series are both first-class.
- **R5 — Support persistent-connection and long-running workloads.** MQTT brokers, the LoRaWAN
  network server and multi-minute synchronisation jobs.
- **R6 — Keep authorization in a single place.** One guard, one code path, one test suite.
- **R7 — Keep exit cost near zero.** Changing provider must be a hardware change, not an
  architectural one.
- **R8 — Fit a single operator.** Low novelty, small surface area to debug.

---

## Considered options

1. **Option A — Keep the current Docker Compose deployment.** Ship the same service set on whatever
   host is chosen, self-managed.
2. **Option B — Adopt Supabase.** Two sub-variants were considered and are analysed separately,
   because they have very different consequences:
   - **B1 — Supabase as the platform.** Rewrite the backend as Supabase-native: PostgREST-generated
     APIs, Row-Level Security for authorization, Edge Functions for custom logic, Supabase Auth for
     identity.
   - **B2 — Supabase as managed PostgreSQL only.** Keep the Spring Boot application hosted
     elsewhere, using Supabase purely as the relational database.

---

## Option analysis

### What Supabase actually is

Supabase is a Backend-as-a-Service built around PostgreSQL. It bundles managed PostgreSQL, an
auto-generated REST API over the schema (PostgREST), authentication, S3-style storage, realtime
subscriptions, Row-Level Security-based authorization and Edge Functions. The platform is open
source and can be self-hosted as a Docker Compose stack.

The decisive technical fact for this decision: **Supabase provides no runtime for arbitrary
containers or JVM workloads.** Edge Functions run TypeScript/JavaScript on the Deno runtime. There is
no place in Supabase where a Spring Boot JAR executes. Consequently, "deploying the Conluz backend on
Supabase" is not a deployment choice at all — it is either a rewrite (B1) or a database-only
arrangement (B2).

### Option A — Keep Docker Compose

**Advantages**

- Runs the existing codebase with no changes; no rewrite risk, no re-verification of domain
  invariants.
- Hexagonal architecture preserved: business rules stay in Java, unit-testable without
  infrastructure, integration-tested with Testcontainers against the real engines.
- Both persistence engines are first-class, in the same topology, with the same lifecycle.
- Native support for MQTT and long-running scheduled jobs.
- Fully self-hostable: `git clone` plus `docker compose up` reproduces the platform. Satisfies the
  AGPL commitment and the data-sovereignty argument sold to public-sector clients.
- Zero vendor lock-in: the unit of portability is the compose file plus a restic restore.
- Existing backup and DR tooling stays valid.
- Single, well-understood failure surface for one operator: a container is either running or not.

**Disadvantages**

- All operational responsibility is ours: patching, upgrades, capacity, certificates, monitoring.
- No point-in-time recovery for PostgreSQL out of the box; RPO is bounded by backup frequency.
- No automatic failover. A host failure means a manual recovery procedure.
- Deployment causes a short downtime window; there is no built-in rolling update.
- Identity, password reset flows and OAuth remain our code to maintain.

### Option B1 — Supabase as the platform (full rewrite)

**Advantages**

- Removes CRUD, authentication and API-layer code from our maintenance surface.
- Managed PostgreSQL with point-in-time recovery, connection pooling and no capacity management.
- Built-in identity: OAuth providers, email flows, session handling.
- Realtime subscriptions available without additional infrastructure.
- Faster delivery for genuinely simple CRUD features.

**Disadvantages**

- **Violates R1 and R3.** Requires business logic be redistributed across
  PostgreSQL triggers, RLS policies and Deno functions. That is business logic embedded in
  infrastructure, which is precisely what the hexagonal architecture exists to prevent.
- **Violates R6.** Current access control would be replaced by per-table RLS policies. The
  deliberate asymmetry of the authorization model, and the rule that the platform-admin flag never
  grants operational-data access, would have to be re-expressed and re-tested policy by policy.
  One authorization source becomes N.
- **Forces a lose-lose choice on R2.** Supabase is genuinely self-hostable — it ships as an
  Apache-licensed Docker Compose stack — so R2 is not technically blocked. The problem is that
  either resolution is bad:
  - *Depend on Supabase Cloud*: the reference deployment requires a commercial account, which
    contradicts the AGPL positioning and the sovereignty argument sold to public-sector clients.
  - *Self-host Supabase*: R2 is preserved, but every operational benefit disappears. The service
    count roughly doubles (PostgreSQL, GoTrue, PostgREST, Realtime, Storage, imgproxy, Kong,
    Studio, meta, edge runtime, log collector, connection pooler), managed backups and
    point-in-time recovery are cloud-only and do not travel, and the self-hosted database image
    carries its own manual major-version upgrade path — the default image moved from Postgres 15
    to Postgres 17 in June 2026 with no automatic data upgrade. Every self-hosting community
    inherits that burden.

  A third risk appears in the mixed case: if the managed service runs on Supabase Cloud while
  communities self-host, the two are **not the same substrate**. Extension availability already
  differs between them (see the TimescaleDB point below). Supporting two platforms with one
  maintainer is not viable.
- **Violates R4, and the obvious workaround is closing.** Supabase does not cover time series.
  Using the `timescaledb` extension to consolidate series into PostgreSQL was evaluated and
  rejected on three grounds:
  1. Supabase has deprecated the extension. It is absent from the Postgres 17 bundle, projects
     still using it must drop it before upgrading, and the official guidance is to migrate
     hypertables to native partitioning with `pg_partman`. The self-hosted PostgreSQL image does
     not ship it either; supplying a custom image is explicitly not fully supported.
  2. Supabase only ever offered the **Apache 2 Edition**, which excludes continuous aggregates,
     compression and retention policies — precisely the features that would justify the migration
     for energy time series.
  3. The root cause is licensing: the Timescale License forbids offering the software as a
     database-as-a-service, so no managed provider can supply the Community Edition. That
     restriction is what makes this a structural dead end on any hosted platform, not a temporary
     gap.

  The consequence is that InfluxDB (or a replacement) would still be hosted and operated
  separately, producing a *more* fragmented architecture rather than a simpler one.
- **Violates R5.** Edge Functions are ephemeral invocations. Mosquitto and multi-minute
  synchronisation jobs would remain outside Supabase on a server we still operate.
- **Violates R7.** This is a one-way door. Leaving means rewriting, not redeploying.
- **Violates R8.** Requires fluency in RLS, PostgREST semantics and Deno, in addition to the JVM
  stack that does not disappear.
- Discards working, tested code with no functional gain for the end user.

### Option B2 — Supabase as managed PostgreSQL only

**Advantages**

- Point-in-time recovery and managed backups for relational state.
- No rewrite; the application keeps its architecture.
- Connection pooling provided.

**Disadvantages**

- Solves only one of several operational concerns. InfluxDB, MQTT, the reporting service
  and the application itself still need a host and still need operating.
- Introduces network latency between the application and its primary database, which matters for
  the transactional cascade operations in the sharing-agreement domain.
- Pays for a bundle — auth, storage, realtime, Edge Functions, PostgREST — of which nothing is used.
- Any provider offering managed PostgreSQL satisfies the same need, usually at lower cost, so
  Supabase carries no specific advantage in this variant.
- Weakens R2: the reference deployment would depend on a hosted database that a self-hosting
  community must replace.

---

## Decision

**We keep the current Docker Compose deployment model and the existing Java codebase. Supabase is
rejected in both variants.**

The reasoning, in order of weight:

1. **Supabase cannot host this backend.** There is no JVM runtime. The comparison is therefore not
   "current deployment vs. Supabase deployment" but "current deployment vs. rewriting a working
   product". A rewrite must be justified by a problem the current design cannot solve, and no such
   problem exists.

2. **The value of this codebase is concentrated exactly where Supabase would dissolve it.** The
   difficult part of Conluz is not CRUD — it is the complexity of domain logic and
   the authorization model. Supabase accelerates CRUD, which is the part that is already cheap, and
   forces the difficult part into triggers, RLS policies and edge functions, where it is harder to
   express, harder to test and impossible to reason about in isolation.

3. **The operational relief is smaller than it appears.** Supabase covers the relational database
   and identity. It does not cover the time series and the scheduled synchronisation
   jobs. The host we currently operate would still be operated. We would
   add a dependency without removing a responsibility.

4. **Reversibility.** The current model can be moved between providers by copying a compose file and
   restoring a backup. Option B1 cannot be reversed at all without a second rewrite.

---

## Consequences

### Positive

- The domain layer remains framework-independent and fully testable.
- Access control remains the single authorization source.
- Self-hostability, and therefore the AGPL positioning, is preserved intact.
- Existing backup and disaster-recovery tooling remains valid without rework.
- Provider choice remains a commodity decision, revisitable at any time.

### Negative — accepted explicitly

- **We own all operational work.** Patching, upgrades, monitoring and capacity planning stay with us
  and consume maintainer capacity that could otherwise go to product work.
- **No point-in-time recovery for PostgreSQL.** RPO is bounded by backup frequency. This is accepted
  on the basis that the current verified RPO is adequate for the data involved; it must be
  re-evaluated if a contractual RPO is ever committed to a client.
- **No automatic failover.** Host failure requires manual recovery. Recovery time is bounded by the
  DR runbook, not by an SLA.
- **Deployments have a downtime window.** Acceptable while usage patterns are not continuous.
- **Identity remains our code.** Password reset, session handling and any future OAuth support are
  work we will have to do ourselves.

### Neutral

- Managed PostgreSQL from a provider other than Supabase remains an open, independent decision. It
  would not affect the architecture, only the operational profile, and is therefore not foreclosed
  by this ADR.
- **Consolidating time series into PostgreSQL is a separate, still-open question.** Adopting
  TimescaleDB (Community Edition, self-hosted, with compression and continuous aggregates
  available) inside the current Docker Compose deployment is compatible with this decision and is
  neither approved nor rejected here. It carries its own costs — re-verifying all DST bucketing
  behaviour, changing the Telegraf output path, rewriting every InfluxQL query in dashboards and
  reports, no ARM builds of the Timescale toolkit, and a source-available rather than free-software
  licence in the reference deployment. It should be decided in its own ADR, most naturally when the
  migration to 15-minute granularity forces the time-series layer open.

---

## Revisit triggers

This decision should be reopened if **any** of the following becomes true:

- A client contract commits to an RPO or availability target that the current backup and recovery
  model cannot demonstrably meet.
- Identity requirements grow beyond what is reasonable to maintain in-house — for example,
  mandatory SSO federation with a public administration's identity provider.
- Operational maintenance measurably displaces product development over a sustained period.
- The time-series engine is replaced for independent reasons. That would remove one of the
  arguments above (R4), so the remaining case against Supabase should be re-stated rather than
  assumed to still hold.

None of these hold at the time of writing.

---

## Notes

- The rejection of Supabase is not a judgement of the product. Supabase is a strong fit for
  applications whose logic is predominantly CRUD over relational data and whose teams have no
  backend. Conluz is the opposite case on both counts.
- Nothing here prevents adopting individual PostgreSQL capabilities that Supabase popularised
  (for example, RLS as *defence in depth* behind current access control guards) if a specific need arises.
  Such a change would be additive and would not relocate authorization logic out of the domain.
