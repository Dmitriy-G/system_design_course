# Capacity Plan — Order Service

## Baseline Metrics

| Metric | Value |
|---|---|
| DAU | 2,000,000 |
| Orders per user per day | 0.2 |
| Total orders per day | 400,000 |
| Seconds in a day | 86,400 |
| Peak window (20% of time) | 17,280 seconds |
| Peak orders (80% of actions) | 320,000 |
| RPS Average | 4.63 |
| RPS Peak | 18.52 |
| Average response size | 2 KB |
| Average response time (normal) | 300ms |
| Average response time (degraded) | 1,500ms |

---

## Little's Law — Concurrency Calculation

Little's Law: **Concurrency = RPS × Average Response Time (seconds)**

### Case 1: Normal Operation (response time = 300ms)

| Metric | Formula | Value |
|---|---|---|
| Concurrency Avg | 4.63 × 0.3 | **1.39** |
| Concurrency Peak | 18.52 × 0.3 | **5.56** |
| Concurrency Safe (2x buffer) | 5.56 × 2 | **11.12** |

### Case 2: Degradation — Low Latency from External Payment Service (response time = 1,500ms = 300ms × 5)

When the payment gateway slows down by 5x, requests pile up in the order service:

| Metric | Formula | Value |
|---|---|---|
| Concurrency Avg | 4.63 × 1.5 | **6.95** |
| Concurrency Peak | 18.52 × 1.5 | **27.78** |
| Concurrency Safe (2x buffer) | 27.78 × 2 | **55.56** |

> **Key insight:** A 5x slowdown in the payment service causes a **5x increase in concurrency** inside the order service. Without a circuit breaker or timeout, threads pile up waiting for payment responses, leading to thread pool exhaustion and cascading failure.

---

## Plan 1: Growth x3 — First Year

### Assumptions

The system must handle 3x current load by end of year 1 without architectural changes.

| Metric | Current | x3 Target |
|---|---|---|
| DAU | 2,000,000 | 6,000,000 |
| Orders per day | 400,000 | 1,200,000 |
| RPS Average | 4.63 | 13.89 |
| RPS Peak | 18.52 | 55.56 |
| Concurrency Peak (normal) | 5.56 | 16.67 |
| Concurrency Peak (degraded) | 27.78 | 83.34 |
| Ingress Avg | 4.63 KB/s | 13.89 KB/s |
| Egress Avg | 9.26 KB/s | 27.78 KB/s |
| Egress Peak | 37.04 KB/s | 111.12 KB/s |

### Infrastructure Requirements

| Component | Current | x3 Target | Action |
|---|---|---|---|
| Order Service instances | 1 | 3 | Scale out horizontally |
| Database connections | 10 | 30 | Increase connection pool |
| Redis memory | 256 MB | 768 MB | Upgrade Redis instance |
| Kafka partitions | 3 | 9 | Add partitions |
| Outbox poller threads | 1 | 3 | Scale with service instances |

### Timeline

| Month | Milestone | Action |
|---|---|---|
| Month 1-3 | 1.5x growth | Monitor, no changes needed |
| Month 4-6 | 2x growth | Add second Order Service instance |
| Month 7-9 | 2.5x growth | Scale database read replicas |
| Month 10-12 | 3x growth | Third instance, Redis cluster upgrade |

---

## Plan 2: Marketing Spike x10 — 2 Days

### Assumptions

A planned marketing campaign causes 10x traffic for exactly 48 hours. The system must handle the spike and return to normal automatically.

| Metric | Normal | x10 Spike |
|---|---|---|
| DAU | 2,000,000 | 20,000,000 |
| Orders per day | 400,000 | 4,000,000 |
| RPS Average | 4.63 | 46.3 |
| RPS Peak | 18.52 | 185.2 |
| Concurrency Peak (normal) | 5.56 | 55.6 |
| Concurrency Peak (degraded) | 27.78 | 277.8 |
| Egress Peak | 37.04 KB/s | 370.4 KB/s |

### Infrastructure Requirements

| Component | Normal | x10 Spike | Strategy |
|---|---|---|---|
| Order Service instances | 1 | 10 | Auto-scaling group |
| Database | 1 primary | 1 primary + 3 read replicas | Scale out reads |
| Redis | Single node | Redis Cluster (3 nodes) | Scale out |
| Kafka throughput | 18.52 msg/s | 185.2 msg/s | Pre-scale partitions |
| CDN / API Gateway | Standard | Higher rate limits | Pre-configure |

### Spike Preparation Checklist

- [ ] Enable auto-scaling 24h before campaign launch
- [ ] Pre-warm Redis cache with expected hot data
- [ ] Increase database connection pool size
- [ ] Notify payment gateway provider of expected volume
- [ ] Set circuit breaker thresholds to prevent cascade
- [ ] Configure rate limiting at API Gateway to protect downstream
- [ ] Schedule post-spike scale-down to avoid unnecessary cost

---

## Plan 3: Low Latency from External Payment Service (÷5 degradation)

### Assumptions

Payment gateway response time increases from 100ms to 500ms (5x slower). This directly increases order service response time from 300ms to 1,500ms.

### Impact Analysis using Little's Law

| Scenario | RPS | Response Time | Concurrency | Thread Pool Risk |
|---|---|---|---|---|
| Normal Peak | 18.52 | 300ms | 5.56 | Low |
| Degraded Peak | 18.52 | 1,500ms | 27.78 | High |
| Degraded Peak + Safety | 18.52 | 1,500ms | 55.56 | Critical |

### Without Mitigation — Cascade Failure Timeline

```
Payment gateway slows 5x
        ↓
Order service threads wait longer for payment response
        ↓
Concurrency grows from 5.56 → 27.78
        ↓
Thread pool exhausts (default pool = 200 threads)
        ↓
New requests queue up
        ↓
Queue fills, requests timeout
        ↓
Order service returns 503
        ↓
Full cascade failure
```

### Mitigation Strategies

| Strategy | Implementation | Effect |
|---|---|---|
| Circuit Breaker | Open circuit after 50% failures in 10s window | Stop waiting for slow payment, return fast error |
| Timeout | Set payment client timeout to 500ms | Fail fast instead of waiting 1,500ms |
| Bulkhead | Separate thread pool for payment calls (max 20 threads) | Isolate payment slowdown from order processing |
| Fallback | Queue payment for async retry if gateway is slow | Decouple payment from order creation response |
| Rate Limiting | Limit payment calls to 20 RPS max | Protect payment gateway from overload |

---

## Headroom Table

> **Goal: maintain 30% headroom** — system should only use 70% of capacity at peak load, reserving 30% for unexpected spikes and safe operation.

### Formula

```
Headroom % = ((Capacity - Peak Load) / Capacity) × 100
Required Capacity = Peak Load / 0.70
```

### Current Headroom Analysis

| Resource | Current Capacity | Peak Load | Usage % | Headroom % | Status |
|---|---|---|---|---|---|
| RPS (instances=1, max=26 RPS) | 26 RPS | 18.52 RPS | 71% | **29%** | ⚠️ Below goal |
| Concurrency Normal | 20 threads | 5.56 | 28% | **72%** | ✅ Healthy |
| Concurrency Degraded | 20 threads | 27.78 | 139% | **-39%** | ❌ Critical |
| DB Connections (pool=10) | 10 | 7 | 70% | **30%** | ✅ At goal |
| Redis Memory (256MB) | 256 MB | 128 MB | 50% | **50%** | ✅ Healthy |
| Kafka Throughput | 50 msg/s | 18.52 msg/s | 37% | **63%** | ✅ Healthy |
| Egress Bandwidth (1 Gbps) | 1,000 KB/s | 37.04 KB/s | 4% | **96%** | ✅ Healthy |

### Headroom at x3 Growth

| Resource | Required Capacity (x3 + 30%) | Action |
|---|---|---|
| RPS | 55.56 / 0.70 = **79.4 RPS** | Scale to 4 instances |
| Concurrency Normal | 16.67 / 0.70 = **23.8 threads** | Increase thread pool to 30 |
| Concurrency Degraded | 83.34 / 0.70 = **119 threads** | Add bulkhead, circuit breaker |
| DB Connections | 21 / 0.70 = **30 connections** | Increase pool to 35 |
| Redis Memory | 384 MB / 0.70 = **549 MB** | Upgrade to 1 GB instance |

### Headroom at x10 Spike

| Resource | Required Capacity (x10 + 30%) | Action |
|---|---|---|
| RPS | 185.2 / 0.70 = **264.6 RPS** | Auto-scale to 11 instances |
| Concurrency Normal | 55.6 / 0.70 = **79.4 threads** | Scale thread pool dynamically |
| Concurrency Degraded | 277.8 / 0.70 = **396.9 threads** | Circuit breaker mandatory |
| DB Connections | 70 / 0.70 = **100 connections** | PgBouncer connection pooler |
| Redis Memory | 1,280 MB / 0.70 = **1,829 MB** | Redis Cluster 3-node |

---

## Scaling Solutions

### Solution 1: Horizontal Pod Autoscaling — Scale Out (Order Service)

**Type:** Scale-out
**Trigger:** CPU > 70% or RPS > 15

The most direct solution. Add more Order Service instances behind the load balancer. Each instance handles its own share of requests independently.

**Best for:** Plan 1 (x3 growth), Plan 2 (x10 spike)

```
Load Balancer
    ├── Order Service Instance 1
    ├── Order Service Instance 2
    └── Order Service Instance 3
```

**Metrics to watch:** CPU per instance, RPS per instance, error rate per instance

---

### Solution 2: Database Read Replicas — Scale Out (PostgreSQL)

**Type:** Scale-out
**Trigger:** DB CPU > 60% or read query latency > 100ms

Route all read queries (order status, order history) to read replicas. Write queries (create, update) go to primary only. Combined with Cache-Aside pattern this dramatically reduces primary DB load.

**Best for:** Plan 1 (x3 growth), Plan 2 (x10 spike)

```
Primary DB  ← writes only
    ├── Read Replica 1 ← order status reads
    └── Read Replica 2 ← order history reads
```

**Metrics to watch:** Replication lag, read replica CPU, query distribution

---

### Solution 3: Redis Cluster — Scale Out (Cache)

**Type:** Scale-out
**Trigger:** Redis memory > 70% or cache hit rate drops below 80%

Shard Redis across multiple nodes. Each node owns a subset of keys. Increases total memory and throughput proportionally.

**Best for:** Plan 2 (x10 spike), sustained x3 growth

```
Redis Cluster
    ├── Node 1 (slots 0-5460)
    ├── Node 2 (slots 5461-10922)
    └── Node 3 (slots 10923-16383)
```

**Metrics to watch:** Memory per node, keyspace hits/misses, replication lag

---

### Solution 4: Kafka Partition Scaling — Scale Out (Messaging)

**Type:** Scale-out
**Trigger:** Consumer lag > 1,000 messages or throughput > 80% of partition capacity

Increase Kafka partitions to allow more parallel consumers. More partitions = more consumer threads = higher throughput. Must be done before the spike (partitions cannot be easily reduced).

**Best for:** Plan 2 (x10 spike), Plan 1 (x3 growth)

```
Topic: order.events
    Before: 3 partitions → 3 consumers max
    After:  9 partitions → 9 consumers max
```

**Metrics to watch:** Consumer lag per partition, messages/second, end-to-end latency

---

### Solution 5: Vertical Scaling of Database — Scale Up (PostgreSQL)

**Type:** Scale-up
**Trigger:** Write throughput bottleneck that cannot be solved by read replicas

Upgrade the primary database instance to a larger machine with more CPU, RAM, and faster storage (SSD → NVMe). Effective for write-heavy workloads where read replicas do not help.

**Best for:** Plan 1 (x3 growth) as a quick fix before read replicas are ready

| Instance | vCPU | RAM | Max Connections | Write IOPS |
|---|---|---|---|---|
| db.t3.medium | 2 | 4 GB | 100 | 3,000 |
| db.r6g.large | 2 | 16 GB | 500 | 6,000 |
| db.r6g.xlarge | 4 | 32 GB | 1,000 | 12,000 |

**Metrics to watch:** Write IOPS, CPU, connections used vs max

---

### Solution 6: Circuit Breaker + Bulkhead — Scale Resilience (Payment Gateway)

**Type:** Resilience pattern (not traditional scaling)
**Trigger:** Payment gateway latency > 500ms or error rate > 10%

Isolate the payment gateway calls in a dedicated thread pool (bulkhead). If the gateway is slow, only the payment bulkhead is affected — the rest of the order service continues operating. Circuit breaker opens after threshold is reached, returning fast errors instead of waiting.

**Best for:** Plan 3 (payment service degradation)

```
Order Service Thread Pool (200 threads)
    ├── General pool (180 threads) ← order processing
    └── Payment bulkhead (20 threads) ← payment calls only
            ↓
        Circuit Breaker
            ├── CLOSED: forward to payment gateway
            ├── OPEN: return fallback immediately
            └── HALF-OPEN: test with 1 request
```

**Metrics to watch:** Circuit breaker state changes, bulkhead rejections, payment timeout rate

---

### Solution 7: PgBouncer Connection Pooling — Scale Out (DB Connections)

**Type:** Scale-out (connection layer)
**Trigger:** DB connection pool exhaustion during x10 spike

PostgreSQL has a hard limit on concurrent connections. PgBouncer sits between the application and database, multiplexing many application connections onto fewer database connections. Allows hundreds of application instances to share a small database connection pool.

**Best for:** Plan 2 (x10 spike) where many Order Service instances compete for DB connections

```
10 Order Service instances × 10 connections = 100 app connections
        ↓
    PgBouncer (pool_size = 20)
        ↓
    PostgreSQL (max_connections = 25)
```

**Metrics to watch:** Pool wait time, pool size vs active, client wait queue

---

### Solution 8: API Gateway Rate Limiting — Scale Protection

**Type:** Scale-protection
**Trigger:** Before any planned spike (marketing campaign)

Configure rate limits at the API Gateway to protect downstream services. Prevents a single burst of traffic from overwhelming the order service, database, or payment gateway. Smooth out spikes by queuing excess requests rather than dropping them.

**Best for:** Plan 2 (x10 marketing spike), preventing cascade failures in Plan 3

| Limit Type | Normal | x10 Spike Config |
|---|---|---|
| Global RPS | 50 RPS | 200 RPS |
| Per-user RPS | 5 RPS | 10 RPS |
| Burst allowance | 2x for 5s | 1.5x for 10s |
| Queue size | 100 requests | 500 requests |

**Metrics to watch:** Rate limit hit rate, queue depth, request rejection rate

---

## Scaling Solutions Summary

| Solution | Type | Plan 1 (x3) | Plan 2 (x10) | Plan 3 (degraded) | Complexity |
|---|---|---|---|---|---|
| 1. HPA Order Service | Scale-out | ✅ | ✅ | ⚠️ | Low |
| 2. DB Read Replicas | Scale-out | ✅ | ✅ | ❌ | Medium |
| 3. Redis Cluster | Scale-out | ⚠️ | ✅ | ❌ | Medium |
| 4. Kafka Partitions | Scale-out | ✅ | ✅ | ❌ | Low |
| 5. DB Vertical Scale | Scale-up | ✅ | ⚠️ | ❌ | Low |
| 6. Circuit Breaker | Resilience | ⚠️ | ⚠️ | ✅ | Medium |
| 7. PgBouncer | Scale-out | ⚠️ | ✅ | ❌ | Low |
| 8. Rate Limiting | Protection | ⚠️ | ✅ | ✅ | Low |

✅ Highly recommended  ⚠️ Optional / helpful  ❌ Not applicable

---

## Review Schedule

| Trigger | Action |
|---|---|
| Monthly | Review headroom metrics, adjust if any resource below 30% headroom |
| At 2x growth | Execute Plan 1 Phase 2 actions |
| 2 weeks before marketing campaign | Execute Plan 2 preparation checklist |
| Payment gateway SLA drops | Activate Plan 3 circuit breaker thresholds |
| Any resource hits 80% utilization | Immediate scaling action required |