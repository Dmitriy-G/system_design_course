# ADR-003 Read caching methods

- Status: Accepted
- Date: 2026-02-21
- Owners: Backend Team

## Context

The order service currently queries the database directly for every request. With DAU of 2,000,000 users and 400,000 orders per day, database load is becoming a bottleneck.

Profiling shows that:

- 70% of read requests are for order status checks (same data read multiple times)
- Database P99 query latency is 180ms contributing significantly to overall 3,200ms response time
- Database CPU utilization reaches 85% during peak traffic (RPS Peak 18.52)
- Order status data changes infrequently after creation (mostly read-heavy workload)

The team needs to introduce a caching layer using Redis to reduce database load and improve read latency. The decision is which caching strategy to adopt.

## Decision

We will use Cache-Aside (Lazy Loading) as the primary caching strategy for the order service.

The application will be responsible for managing the cache:

- On read: check cache first, if miss query database and populate cache
- On write: update database first, then invalidate or update cache entry
- TTL of 5 minutes for order status cache entries

## Consequences

### Positive

- Database query load reduced by estimated 60-70% for order status reads
- Order status read latency drops from ~180ms to ~2ms on cache hit
- Cache failure does not bring down the service (graceful degradation)
- Only frequently accessed data occupies cache memory (lazy loading)
- Simple implementation with Spring Cache and Redis annotations

### Negative

- Cache miss on first request still hits database (cold start problem)
- Risk of stale data between cache TTL expiry and next refresh
- Application code must handle cache invalidation explicitly on order updates
- Cache stampede risk when many requests miss cache simultaneously for the same key

## Alternatives Considered

### Option 1: Cache-Aside (chosen)

- Application controls all cache interactions explicitly
- Cache only contains data that has actually been requested
- Simple to implement with Redis and Spring Cache
- On cache failure, application falls back to database transparently 

Accepted because it gives full control and graceful degradation

### Option 2: Read-Through

- Cache sits between application and database
- Cache automatically loads data on miss
- Reduces boilerplate code in application layer
- Requires cache provider that supports read-through (e.g. NCache, Coherence)
- Redis does not natively support read-through without additional libraries

Rejected because it adds infrastructure complexity and reduces team control over cache behavior

### Option 3: Write-Through

- Every write goes to cache and database simultaneously
- Cache is always consistent with database
- High write overhead — order service has more reads than writes

Rejected because write amplification is not justified for a read-heavy workload

### Option 4: No caching

- Simplest approach
- Does not solve database bottleneck at peak load

Rejected because database CPU at 85% during peak is unsustainable at 10x growth

## Observability / Metrics

Cache hit rate: Above 80% ((Cache hits / Total reads) × 100)

Cache miss rate: Below 20% ((Cache misses / Total reads) × 100)

Cache read latency: Below 5ms (Redis latency monitoring)

Database read latency: Below 50ms (APM query monitoring)

## Risks

### Stale order status served to user

Probability: Medium

Impact: High

Mitigation: Set appropriate TTL and invalidate on every order update

### Cache stampede on TTL expiry

Probability: Medium

Impact: Medium

Mitigation: Implement mutex lock and TTL jitter

### Redis unavailability

Probability: Low

Impact: Medium

Mitigation: Implement fallback to database on Redis connection failure

### Cache memory overflow

Probability: Low

Impact: Medium

Mitigation: Set max memory policy to all keys-lru in Redis

### Cold start on deployment

Probability: High

Impact: Low

Mitigation: Pre-warm cache after deployment with most accessed orders

## Related Decisions