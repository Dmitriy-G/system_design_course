# ADR-004: Scale-Up vs Scale-Out Strategy for Core API — 12 Month Horizon

- Date: 2026-02-21
- Owners: Backend Team

## Status
Accepted

## Context

The Order Service Core API currently runs on a single instance and serves:

- DAU: 2,000,000 users
- RPS Average: 4.63
- RPS Peak: 18.52
- Concurrency Peak (normal): 5.56
- Concurrency Peak (degraded): 27.78
- Concurrency Safe: 11.12

According to the Capacity Plan (see capacity_plan.md), the system must handle
three growth scenarios over the next 12 months:

| Scenario | RPS Peak | Concurrency Peak | Timeline |
|---|---|---|---|
| Baseline | 18.52 | 5.56 | Now |
| x3 organic growth | 55.56 | 16.67 | Month 12 |
| x10 marketing spike | 185.2 | 55.6 | Any time (2-day event) |
| Payment degradation ÷5 | 18.52 | 27.78 | Unpredictable |

The current single-instance architecture has a headroom of only **29%** at peak
RPS (26 RPS capacity vs 18.52 RPS peak), which is below the 30% headroom goal
defined in the SLO. Any further organic growth will breach the SLO without
intervention.

The team must decide: should the Core API scale by upgrading to a larger machine
(scale-up), by adding more identical instances (scale-out), or by combining both
approaches across different phases of the 12-month roadmap?

Key constraints identified:

- Team size: 8 developers, growing to 12 by month 6
- Deployment pipeline: GitHub Actions CI/CD with Gradle + Spring Boot
- Architecture: Modular Monolith (ADR-002), planned microservices extraction in Phase 2
- State: Order Service is stateless — session state is stored in Redis, persistence in PostgreSQL
- Budget: Infrastructure cost must stay proportional to growth (no 10x cost for 3x traffic)
- SLO targets: 99.9% availability, P95 latency under 1,000ms for pay flow, 30% headroom

## Decision

We will adopt a **hybrid approach: Scale-Up first (months 1–3), then Scale-Out
from month 4 onward**, with auto-scaling enabled for spike handling at any point.

The rationale is:

- Scale-Up in months 1–3 is the fastest path to restore 30% headroom with zero
  code or infrastructure changes — just a machine resize
- Scale-Out from month 4 provides the elasticity needed for x3 organic growth
  and unpredictable x10 spikes without hitting vertical limits
- Auto-scaling handles the marketing spike and payment degradation scenarios
  regardless of which phase the system is in

## Consequences

### Positive

#### Immediate headroom restoration (Phase 1)

The scale-up in months 1–3 immediately brings headroom from 29% back above the
30% SLO target with no code changes, no deployment pipeline changes, and no
risk of introducing distributed systems bugs during a period of active feature
development.

#### Elimination of single point of failure (Phase 2)

Introducing two instances behind a load balancer in month 4 means a single
instance crash no longer causes a full outage. The load balancer detects the
failure and routes all traffic to the surviving instance within seconds,
preserving the 99.9% availability SLO.

#### Elastic spike handling (Phase 3)

Auto-scaling absorbs the x10 marketing spike (185.2 RPS) and payment gateway
degradation (27.78 concurrency peak) without manual intervention. The system
scales from 2 to 10 instances within 6 minutes of trigger, then scales back
down automatically, keeping spike costs isolated to the 2-day event window.

#### Cost optimization at x3 scale (Phase 4)

Switching from 2 × large instances to 3 × smaller instances in months 7–12
reduces monthly cost from ~$400 to ~$300 while increasing total capacity and
improving fault isolation. Horizontal scaling is more cost-linear than vertical
scaling at this load range.

#### Alignment with microservices roadmap

Scale-out infrastructure (load balancer, rolling deploys, health checks,
auto-scaling groups) built in Phase 2–3 is directly reusable when extracting
microservices in ADR-002 Phase 2. The team gains operational experience with
distributed deployments before the architecture becomes more complex.

#### Stateless design validation

Successfully running multiple Order Service instances confirms the service is
truly stateless — no local state, no sticky sessions, no in-memory caches that
diverge between instances. This is a prerequisite for safe microservices
extraction.

### Negative

#### Short-term cost inefficiency (Phase 1)

The Phase 1 scale-up to 8 vCPU / 16 GB costs ~$200/month. When Phase 4
downsizes to 4 vCPU / 8 GB instances, the larger instance size is no longer
needed. The 3-month overspend (~$450 total) is accepted as the cost of buying
time while horizontal infrastructure is prepared.

#### Increased operational complexity from Phase 2

Adding a load balancer introduces new failure modes: misconfigured health
checks, uneven traffic distribution, and connection draining during rolling
deploys. The team must invest time in load balancer monitoring, tuning, and
runbooks before Phase 2 go-live.

#### Rolling deployment risk

During a rolling deploy with 2 instances, capacity temporarily drops to 50%.
If a deploy coincides with a traffic spike, the single active instance may
briefly exceed its safe concurrency limit. Mitigation: enforce deploy freeze
windows during known peak periods.

#### Auto-scaling lag for sudden spikes

Auto-scaling reacts to observed metrics with a 2–3 minute trigger delay plus
instance startup time. A completely sudden x10 traffic spike (e.g., a viral
event with no warning) may cause elevated error rates for 5–6 minutes before
capacity catches up. Pre-scaling 24 hours before known events mitigates this
for planned campaigns but not for unplanned spikes.

#### Database and Redis are not scaled by this decision

This ADR covers only the Order Service API instances. At x3 growth, the
database connection pool (currently 10 connections) and Redis memory (256 MB)
will also need scaling per the Capacity Plan. Those are separate concerns not
addressed here and must be tracked separately.

#### Two infrastructure transitions in 12 months

The hybrid approach requires two infrastructure changes (resize in month 1,
scale-out in month 4) instead of one. Each change carries deployment risk and
requires team attention. The team must maintain discipline to execute Phase 2
before organic growth reaches the Phase 1 vertical limit.

### Neutral

#### No application code changes required throughout

The Order Service code does not change in any phase. Statelessness, Redis-based
session storage, and the outbox pattern (ADR-004) already make the service
horizontally scalable without modification.

#### SLO targets remain unchanged

This decision does not relax any SLO targets. The 99.9% availability, P95 < 1,000ms
latency, and 30% headroom goals remain the acceptance criteria for every phase.

## Alternatives Considered

### Option 1: Scale-Up Only (rejected for long term)

Upgrade instance size progressively throughout the 12 months without adding
instances.

**Pros:**
- Zero application changes required
- Simpler operations — one instance to monitor and deploy
- Eliminates distributed systems complexity (no load balancer needed initially)
- Faster time to restore headroom (resize takes minutes)

**Cons:**
- Hard vertical ceiling — no single machine can handle x10 spike cost-effectively
- Single point of failure — one instance means 0% availability during deployments
  and instance failures
- Cost efficiency degrades at high tiers — a 32-core instance costs more than 4×
  an 8-core instance for the same total capacity
- Does not align with modular monolith → microservices migration (ADR-002)
- Accepted as short-term fix for months 1–3 only

### Option 2: Scale-Out Only from Day 1 (rejected as premature)

Add a second instance and load balancer immediately, then grow horizontally.

**Pros:**
- No vertical ceiling
- Eliminates single point of failure immediately
- Elastic — can scale to any RPS with enough instances
- Aligns with microservices extraction roadmap

**Cons:**
- Introduces load balancer, session affinity concerns, and distributed health
  checks before the team has experience with them
- Current load (4.63 RPS avg) does not justify the operational overhead today
- Deployment complexity increases (rolling deploys, health check tuning needed)
- Rejected because current load does not justify the operational cost today

### Option 3: Hybrid Scale-Up then Scale-Out (chosen)

Use Scale-Up to restore headroom quickly in months 1–3, then introduce
Scale-Out in month 4 as load approaches the vertical limit.

**Pros:**
- Restores 30% headroom immediately with no code changes
- Defers horizontal complexity until the team is ready
- Scale-Out investment aligns with x3 growth and microservices migration
- Auto-scaling handles spike scenarios at any phase

**Cons:**
- Two infrastructure changes across 12 months instead of one
- Short-term scale-up cost will be partially wasted when scale-out replaces it
- Requires careful monitoring to trigger scale-out at the right moment

**Accepted** because it balances speed of delivery, operational simplicity in
the early months, and long-term scalability.

### Option 4: Serverless / FaaS (rejected)

Move order processing to serverless functions (AWS Lambda, Google Cloud Run).

**Pros:**
- Infinite scale-out with no infrastructure management
- Pay per request — very cost-efficient at low load

**Cons:**
- Cold start latency conflicts with P95 < 1,000ms SLO for pay flow
- Spring Boot startup time (3-5 seconds) is incompatible with serverless cold starts
- Stateful connections (JDBC, Redis) are expensive to manage in serverless
- Rejected — incompatible with current tech stack and latency SLO

## Observability / Metrics

### Phase 1 — Scale-Up (Months 1–3)

| Metric | Target | Measurement |
|---|---|---|
| RPS headroom | ≥ 30% at peak | (Capacity - Peak) / Capacity × 100 |
| P95 latency (pay flow) | < 1,000ms | APM tool (Datadog / New Relic) |
| Availability | ≥ 99.9% | MTBF / (MTBF + MTTR) |
| Instance CPU at peak | < 70% | Infrastructure monitoring |
| Resize downtime | < 5 minutes | Deployment log |

### Phase 2 — Scale-Out (Months 4–6)

| Metric | Target | Measurement |
|---|---|---|
| Zero-downtime deployments | 100% | Deployment pipeline success rate |
| RPS distribution variance | < 10% between instances | Load balancer metrics |
| Single instance failure recovery | < 30 seconds | Health check + LB failover |
| Availability during rolling deploy | 99.9% | Uptime monitoring |

### Phase 3 — Auto-Scaling (Months 4–12)

| Metric | Target | Measurement |
|---|---|---|
| Auto-scale trigger to first new instance | < 3 minutes | Auto-scale event log |
| x10 spike fully absorbed | < 6 minutes | Load test results |
| Scale-in after spike | Within 15 minutes | Auto-scale event log |
| Over-provisioning cost (idle instances) | < 20% of total compute cost | Cloud billing |

### Phase 4 — Sustained x3 Growth (Months 7–12)

| Metric | Target | Measurement |
|---|---|---|
| RPS headroom at x3 load | ≥ 30% | Capacity planning review |
| Cost per RPS | ≤ $2/RPS/month | Cloud billing / peak RPS |
| Instance count at baseline | 3 | Infrastructure dashboard |
| Compute cost vs Phase 1 | < 2x cost for 3x capacity | Cloud billing |

---

## Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Scale-up does not restore headroom fast enough | Low | High | Pre-schedule resize in month 1, monitor daily |
| Load balancer misconfiguration causes uneven distribution | Medium | Medium | Test with load testing before Phase 2 go-live |
| Auto-scaling too slow for sudden x10 spike | Medium | High | Pre-scale 24h before known marketing events |
| Scale-in too aggressive, instances removed during residual peak | Low | Medium | Set conservative scale-in cooldown (10 min) |
| Database becomes bottleneck before API instances | Medium | High | Add read replicas in Phase 2 (see Capacity Plan) |
| Rolling deploy causes brief capacity drop below SLO | Low | Medium | Ensure min 2 healthy instances during deploy |
| Phase 1 scale-up cost wasted when Phase 4 downsizes | Low | Low | Accepted cost — buys time and safety in months 1–3 |

## Related Decisions