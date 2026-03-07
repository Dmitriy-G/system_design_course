# Service Level Objectives — Order Service

**Service:** Order Service
**Owner:** Platform Engineering Team
**Review Period:** Rolling 30 days
**Last Updated:** 2026-02-27
**Status:** Draft

---

## Table of Contents

1. [Availability](#1-availability)
2. [Latency](#2-latency)
3. [Reliability](#3-reliability--incidents)
4. [Maintainability](#4-maintainability)
5. [Scalability](#5-scalability)
6. [Security](#6-security)
7. [Network Throughput](#7-network-throughput)
8. [Integrations](#8-integrations)
9. [Error Budget Summary](#9-error-budget-summary)

---

## 1. Availability

### Definition

The percentage of time the order service is reachable and successfully processing
requests. Measured as the ratio of healthy uptime to total time in a rolling 30-day window.
Payment processing must complete within 60 seconds from initiation to confirmation.

### SLI

```
Availability = MTBF / (MTBF + MTTR)
```

### SLO Target

| Metric | Target |
|---|---|
| Availability | **99.9%** |
| Allowed downtime per month | **43 minutes** |
| MTBF | 7,200 minutes |
| MTTR | 30 minutes |

### Example Calculation

```
A = 7,200 / (7,200 + 30)
A = 7,200 / 7,230
A = 99.58%
```

> ⚠️ **Note:** Current MTBF of 7,200 min gives 99.58% availability which is
> **below** the 99.9% target. To reach 99.9%, MTBF must increase to ~30,000 min
> or MTTR must decrease to under 3 minutes.

### Error Budget

```
Error budget = 100% - 99.9% = 0.1%
Allowed downtime = 43,200 min × 0.001 = 43.2 minutes per month
```

### Breach Policy

If availability drops below 99.9%:
- Incident post-mortem within 5 business days
- No new feature deployments until root cause is resolved

---

## 2. Latency

### Definition

Latency is measured at the UX level and includes: page rendering, HTTP requests
between services, JDBC queries, HTTP requests to external APIs, and failover time
when switching to the reserve payment gateway.

### SLI

```
P95 = histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
```

### SLO Targets

| User Action | Metric | Target |
|---|---|---|
| Click Order button → show details form | P95 | **500ms** |
| Click Pay button → move to paid screen | P95 | **1,000ms** |

> ⚠️ **Note:** The original draft listed P95 as 0.5ms for the order button which
> is physically impossible (network round-trip alone is 1-50ms). Corrected to
> **500ms**.

### Breakdown Budget per Action

| Step | Budget | Notes |
|---|---|---|
| API Gateway routing | 10ms | |
| Order Service processing | 150ms | |
| Database query | 50ms | |
| Redis cache read | 5ms | On cache hit |
| Payment gateway call | 700ms | External, highest variance |
| Response serialization | 10ms | |
| **Total P95 budget** | **925ms** | Under 1,000ms target |

### Error Budget

```
P95 latency budget: 5% of requests may exceed the target per 5-minute window
```

---

## 3. Reliability / Incidents

### Definition

The probability that the order service processes requests without failure during
a given time period. Only failures caused by technical reasons are counted —
user errors and intentional cancellations are excluded.

### SLI

```
Successful order rate = (Successfully processed orders / Total order attempts) × 100

Reliability over time = e^(-t / MTBF)
where e = 2.718, t = time period in minutes
```

### SLO Targets

| Metric | Target |
|---|---|
| Successful order rate per day | **95%** |
| MTBF | **43,000 minutes (~30 days)** |
| Reliability over 1 day (1,440 min) | **96.75%** |

### Example Calculation

```
R = 2.718 ^ (-1,440 / 43,000)
R = 2.718 ^ (-0.033)
R = 0.9675
R = 96.75%
```

### Reliability over Different Time Windows

| Time Period | Formula | Reliability |
|---|---|---|
| 1 hour (60 min) | 2.718^(-60/43,000) | 99.86% |
| 6 hours (360 min) | 2.718^(-360/43,000) | 99.17% |
| 1 day (1,440 min) | 2.718^(-1,440/43,000) | 96.75% |
| 3 days (4,320 min) | 2.718^(-4,320/43,000) | 90.44% |
| 7 days (10,080 min) | 2.718^(-10,080/43,000) | 79.10% |

### Incident Classification

| Severity | Definition | Response Time |
|---|---|---|
| P1 | Order service fully down | 15 minutes |
| P2 | Error rate above 10% | 30 minutes |
| P3 | Error rate 5-10% | 2 hours |
| P4 | Single non-critical failure | Next business day |

---

## 4. Maintainability

### Definition

The ability to fix problems and deliver changes to production quickly and safely.
Covers deployment reliability, test coverage, and recovery speed.

### SLIs

```
Deployment success rate D = (Successful deployments / Total attempts) × 100

Test coverage C = (Lines covered by tests / Total lines of code) × 100

MTTR = Total downtime (minutes) / Number of incidents
```

### SLO Targets

| Metric | Target |
|---|---|
| Deployment success rate | **96%** |
| Test coverage | **85%** |
| MTTR | **≤ 30 minutes** |

### Example Calculations

```
D = (48 / 50) × 100 = 96%

C = (8,500 / 10,000) × 100 = 85%

MTTR = 150 / 5 = 30 minutes
```

### Test Coverage Breakdown

| Layer | Target Coverage | Notes |
|---|---|---|
| Domain layer | 90% | Core business logic, highest priority |
| Application layer | 85% | Use cases and orchestration |
| API layer | 80% | Controller and request validation |
| Infrastructure layer | 70% | Adapters, lower priority |
| **Overall** | **85%** | Enforced by ArchUnit + JaCoCo |

### Deployment Pipeline Gates

All of these must pass before production deployment:

- [ ] Unit tests pass (target: 100%)
- [ ] Architecture tests pass (ArchUnit)
- [ ] Test coverage above 85% (JaCoCo)
- [ ] C4 diagrams valid (PlantUML)
- [ ] ADR documents valid (CI validation)

---

## 5. Scalability

### Definition

The ability to handle current load reliably, absorb traffic spikes, and grow
capacity proportionally. Based on DAU of 2,000,000 with 0.2 orders per user per day.

### SLIs

```
RPS_Avg = Total orders per day / Seconds in a day

RPS_Peak = Peak orders / Peak window seconds
(peak = 80% of orders in 20% of time)

Concurrency = RPS × Average response time (Little's Law)
```

### SLO Targets

| Metric | Formula | Value |
|---|---|---|
| Total orders per day | 2,000,000 × 0.2 | **400,000** |
| RPS Average | 400,000 / 86,400 | **4.63 RPS** |
| Peak window | 86,400 × 20% | **17,280 seconds** |
| Peak orders | 400,000 × 80% | **320,000** |
| RPS Peak | 320,000 / 17,280 | **18.52 RPS** |
| Concurrency Avg | 4.63 × 0.3s | **1.39** |
| Concurrency Peak | 18.52 × 0.3s | **5.56** |
| Concurrency Safe | 5.56 × 2 (safety factor) | **11.12** |

> **Latency assumption:** Concurrency uses normal response time of **300ms (0.3s)** matching
> the Capacity Plan baseline. Under payment gateway degradation (5x slowdown → 1,500ms),
> concurrency peak rises to **27.78** and safe threshold to **55.56**. See Capacity Plan
> for full degradation analysis.

### Headroom Targets

> System must maintain **30% headroom** at peak load.

| Resource | Peak Load | Required Capacity (÷0.70) | Status |
|---|---|---|---|
| RPS | 18.52 | **26.5 RPS** | ⚠️ Monitor |
| Concurrency (normal) | 5.56 | **7.94 threads** | ✅ |
| Concurrency (degraded) | 27.78 | **39.7 threads** | ❌ Critical |
| DB connections | 7 | **10 connections** | ✅ |

### Spike Tolerance

| Scenario | RPS | Action |
|---|---|---|
| Normal peak | 18.52 | No action |
| x3 growth | 55.56 | Scale to 3 instances |
| x10 marketing spike | 185.2 | Auto-scale to 10 instances |

---

## 6. Security

### Definition

The ability to detect and block unauthorized access and fraudulent orders in
real time. Comparison with confirmed real-world incidents is performed post-facto
on a monthly basis.

### SLIs

```
Unauthorized blocking rate B = (Blocked unauthorized requests / Total unauthorized attempts) × 100

Fraud detection rate F = (Orders correctly flagged as fraudulent / Total fraudulent orders) × 100
```

### SLO Targets

| Metric | Target |
|---|---|
| Unauthorized access blocking rate | **99.99%** |
| Fraud detection rate | **99.9%** |

### Example Calculations

```
B = (9,999 / 10,000) × 100 = 99.99%

F = (999 / 1,000) × 100 = 99.9%
```

### Security Incident Response

| Event | Response Time | Action |
|---|---|---|
| Unauthorized access spike | 15 minutes | Block IP range, alert security team |
| Fraud rate above 1% | 30 minutes | Enable stricter fraud rules, review flagged orders |
| Data breach suspected | Immediate | Activate incident response plan |

---

## 7. Network Throughput

### Definition

The volume of ingress (incoming requests) and egress (outgoing responses) traffic
processed by the order service. Based on 1 KB average request payload and
2 KB average response payload.

### SLIs

```
Ingress = RPS × request payload size (KB)
Egress  = RPS × response payload size (KB)
Total   = Ingress + Egress
```

### SLO Targets

| Metric | Formula | Value |
|---|---|---|
| Ingress Average | 4.63 × 1 KB | **4.63 KB/s** |
| Ingress Peak | 18.52 × 1 KB | **18.52 KB/s** |
| Egress Average | 4.63 × 2 KB | **9.26 KB/s** |
| Egress Peak | 18.52 × 2 KB | **37.04 KB/s** |
| Total Traffic Average | 4.63 + 9.26 | **13.89 KB/s** |
| Total Traffic Peak | 18.52 + 37.04 | **55.56 KB/s** |

### Daily Traffic Volume

| Metric | Formula | Value |
|---|---|---|
| Daily Egress | 9.26 KB/s × 86,400 | **~0.76 GB/day** |
| Daily Ingress | 4.63 KB/s × 86,400 | **~0.38 GB/day** |
| Total Daily Traffic | | **~1.14 GB/day** |

### Bandwidth Headroom

| Scenario | Peak Traffic | Network Limit | Headroom |
|---|---|---|---|
| Normal | 55.56 KB/s | 1,000,000 KB/s (1 Gbps) | 99.99% ✅ |
| x10 spike | 555.6 KB/s | 1,000,000 KB/s | 99.94% ✅ |

---

## 8. Integrations

### Definition

Integration reliability and cost efficiency for payment gateway providers.
The system has two providers: a primary cheap provider ($0.05/request) and
a reserve expensive provider ($0.50/request).

### SLIs

```
Main provider usage rate R = (Main provider requests / Total requests) × 100

Daily cost C = (Main provider requests × $0.05) + (Reserve provider requests × $0.50)
```

### SLO Targets

| Metric | Target | Notes |
|---|---|---|
| Main provider usage (avg day) | **≥ 90%** | Minimize expensive fallback usage |
| Main provider usage (peak time) | **≥ 50%** | Acceptable degradation during spike |
| Total cost per day | **≤ $250** | Budget ceiling |

### Example Calculations

```
R = (900 / 1,000) × 100 = 90%

C = (600 × $0.05) + (400 × $0.50)
C = $30 + $200
C = $230 per day
```

> ⚠️ **Note:** The original draft had a cost formula error:
> `(600 × 0.50) + (400 × 0.50) = $230`
> The main provider rate is $0.05, not $0.50.
> Corrected formula: `(600 × $0.05) + (400 × $0.50) = $30 + $200 = $230`

### Cost Scenarios

| Scenario | Main provider | Reserve provider | Daily cost |
|---|---|---|---|
| Normal (90% main) | 360,000 req × $0.05 | 40,000 req × $0.50 | **$38,000** |
| Degraded (50% main) | 200,000 req × $0.05 | 200,000 req × $0.50 | **$110,000** |
| Full fallback (0% main) | 0 | 400,000 req × $0.50 | **$200,000** |

> ⚠️ **Note:** At 400,000 orders/day the actual daily costs are significantly
> higher than the $250 target. The $250 target likely applies to a smaller
> subset of requests or a test environment. Clarification needed from business.

### Failover Policy

| Trigger | Action |
|---|---|
| Main provider error rate > 5% | Switch 50% traffic to reserve |
| Main provider error rate > 20% | Switch 100% traffic to reserve |
| Main provider recovery | Gradually shift back over 10 minutes |

---

## 9. Error Budget Summary

| SLO | Target | Error Budget | Burn Alert Threshold |
|---|---|---|---|
| Availability | 99.9% | 43.2 min/month | Alert at 50% burn in 15 days |
| Latency P95 (pay flow) | 1,000ms | 5% of requests | Alert if P95 > 800ms |
| Successful order rate | 95% | 5% failure allowed | Alert if rate drops below 96% |
| Deployment success | 96% | 4% failure allowed | Alert after 2 consecutive failures |
| Test coverage | 85% | — | Block deploy if below 85% |
| Unauthorized blocking | 99.99% | 0.01% miss allowed | Alert immediately on any breach |
| Main provider usage | 90% avg | 10% to reserve | Alert if below 70% for 1 hour |

### Error Budget Policy

- If **≥ 50% of error budget is consumed** in the first 15 days of the month → freeze non-critical feature work, focus on reliability
- If **100% of error budget is consumed** → full freeze on deployments, mandatory incident review
- If **error budget is healthy (< 25% consumed)** → team can take on more deployment risk

---

## Appendix: Issues Found in Original Draft

| Location | Issue | Fix Applied |
|---|---|---|
| Availability | MTBF/MTTR gives 99.58%, below 99.9% target | Added note, target requires MTBF increase |
| Latency | P95 listed as 0.5ms (impossible) | Corrected to 500ms |
| Scalability | Concurrency used 0.5s latency, inconsistent with Capacity Plan 0.3s | Aligned to 0.3s normal response time |
| Scalability | Conc Safe was 18.52 (based on 0.5s), now 11.12 (based on 0.3s) | Updated to match Capacity Plan baseline |
| Reliability | 3-day reliability stated as 90.36%, actual is 90.44% | Corrected to 90.44% |
| Reliability | 7-day reliability stated as 79.23%, actual is 79.10% | Corrected to 79.10% |
| Integrations | Cost formula used wrong rate ($0.50 for main provider) | Corrected to $0.05 for main provider |
| Integrations | $250/day target does not match 400,000 orders/day volume | Added clarification note |
| Reliability formula | Formula written as `2.718^(t/MTBF)` missing negative sign | Correct formula shown with negative exponent |