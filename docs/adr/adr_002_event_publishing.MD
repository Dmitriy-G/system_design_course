# ADR-003 Event publishing methods

- Status: Accepted
- Date: 2026-02-21
- Owners: Backend Team

## Context

The current naive implementation publishes events directly to Kafka immediately after writing to the database.

1. BEGIN transaction
2. INSERT order into PostgreSQL
3. COMMIT transaction
4. Publish event to Kafka  ← outside transaction

- Order saved to database but event never published (Kafka unavailable)
- Event published but database transaction rolled back (duplicate/ghost orders)
- Downstream services processed orders that were later rolled back
- No way to replay missed events without manual intervention

The team needs a reliable, consistent event publishing strategy that guarantees events are always published when and only when the database transaction succeeds.

## Decision

We will adopt the Transactional Outbox Pattern for all event publishing in the order service.

Instead of publishing directly to Kafka, the order service will:

1. Write the order AND the event to the database in the same transaction
2. A separate outbox poller process reads unpublished events from the outbox table
3. Poller publishes events to Kafka and marks them as published

## Consequences

### Positive

- Events are guaranteed to be published if and only if database transaction succeeds
- No more lost events when Kafka is temporarily unavailable
- Events can be replayed from outbox table in case of failures
- Full audit trail of all events published by the order service
- Outbox table acts as a buffer during Kafka downtime

### Negative

- Small additional latency between order write and event publishing (polling interval)
- Additional outbox table adds slight overhead to every order write transaction
- Outbox poller is an additional process to operate and monitor
- Need to handle idempotency on consumer side (at-least-once delivery)
- Outbox table can grow large if poller falls behind — needs cleanup strategy

## Alternatives Considered

### Option 1: Direct Publish

- Simple to implement, no additional infrastructure
- Not atomic — database write and Kafka publish are two separate operations
- Risk of lost events when Kafka is unavailable
- Risk of ghost events when transaction rolls back after publish

Rejected because it violates exactly-once semantics and has caused production incidents

### Option 2: Two-Phase Commit (2PC)

- Distributed transaction across database and Kafka
- Guarantees atomicity across both systems
- Extremely complex to implement and operate
- High performance overhead on every order write
- Kafka does not natively support XA transactions

Rejected because operational complexity far outweighs the benefits

### Option 3: Outbox Pattern with polling (chosen)

- Order and event written atomically in same database transaction
- Outbox poller publishes events asynchronously
- Small additional latency between order creation and event publishing
- Well understood pattern with good library support (Debezium, Spring Modulith)

Accepted because it provides reliability guarantees with manageable complexity

### Option 4: Outbox Pattern with CDC (Change Data Capture)

- Instead of polling, use Debezium to stream database changes directly to Kafka
- Lower latency than polling approach
- Requires additional infrastructure (Debezium connector, Kafka Connect)
- Considered as future evolution once team gains Kafka operational experience
- 
Deferred to Phase 2 migration

## Observability / Metrics

### Event publish success rate

Target: Above 99.99%

Measurement: (Published events / Total outbox events) × 100

### Lost events per day

Target: 0

Measurement: Count of PENDING events older than 10 minutes

### Ghost events per day

Target: 0

Measurement: Reconciliation check between orders and outbox

### Event duplicate rate

Target: Below 0.01%

Measurement: Consumer idempotency tracking

## Risks

### Outbox poller goes down

Probability: Low

Impact: High

Mitigation: Health check alerts, auto-restart, supervisor process

### Outbox table grows unbounded

Probability: Medium

Impact: Medium

Mitigation: Scheduled cleanup job for PUBLISHED events older than 7 days

### Duplicate event processing by consumers

Probability: Medium

Impact: Medium

Mitigation: Consumers must implement idempotency using event id

### Polling interval too slow for real-time needs

Probability: Low

Impact: Medium

Mitigation: Reduce polling interval or migrate to CDC with Debezium

### Database performance impact of outbox table

Probability: Low

Impact: Low

Mitigation: Index on status + created_at, partition table by date

## Related Decisions