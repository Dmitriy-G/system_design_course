# ADR-001 Primary architecture

- Date: 2026-02-21
- Owners: Backend Team

## Status

Accepted

## Context

The order system is being designed from scratch. The team needs to decide on a fundamental architectural approach before development begins. The system is expected to handle:

- DAU of 2,000,000 users
- 400,000 orders per day
- RPS Average of 4.63 and RPS Peak of 18.52
- Multiple business domains: customer management, order management, payment, notifications

The team currently consists of 8 developers. The business expects rapid feature delivery in the first 6 months, followed by scaling to handle 10x growth over the next 2 years.

Key concerns raised during architecture review:

- Time to market is critical — first version must launch in 4 months
- System must handle peak traffic without degradation
- Different parts of the system have very different scaling needs (payment vs notifications)
- Team has mixed experience with distributed systems

## Decision

We will start with a Modular Monolith architecture and migrate to microservices incrementally as the system grows and domain boundaries become clearer.
The monolith will be structured into clearly separated modules:

- Customer Module
- Order Module
- Payment Module
- Notification Module

Each module will have its own internal domain logic and data access layer, but will be deployed as a single application initially.

## Consequences

### Positive

- Faster initial development and delivery (single codebase, no distributed systems overhead)
- Simpler debugging and local development experience
- Lower infrastructure cost at the beginning
- Clear module boundaries make future microservices extraction easier
- Easier to maintain data consistency without distributed transactions
- Smaller team can manage a single deployment pipeline

### Negative

- Single deployment unit means all modules deploy together
- A bug in one module can affect the entire system
- Harder to scale individual modules independently
- Technology stack is shared across all modules
- Risk of modules becoming tightly coupled over time if boundaries are not enforced

## Alternatives Considered

## Option 1: Full Monolith (no module separation)

- Fastest to build initially
- No enforced boundaries between domains
- Will become very hard to maintain as codebase grows

Rejected because it creates high technical debt and makes future microservices extraction very painful

### Option 2: Full Microservices from day one

- Maximum scalability and team autonomy
- Each service can be deployed and scaled independently
- Requires significant infrastructure investment upfront (service discovery, API gateway, distributed tracing, message queues)
- With a team of 8 developers and 4 month deadline this approach introduces too much operational overhead

Rejected because time to market risk is too high and team lacks distributed systems experience

### Option 3: Modular Monolith (chosen)

- Best balance between speed of delivery and architectural cleanliness
- Clear module boundaries allow future extraction into microservices
- Single deployment reduces operational complexity for current team size

Accepted because it meets the 4 month deadline while keeping the architecture evolvable

## Observability / Metrics

- Development & Delivery Metrics
- Performance Metrics
- Reliability Metrics
- Architecture Quality Metrics
- Scalability Metrics
- Migration Readiness Metrics (these metrics track when a module is ready to be extracted into a microservice)
- Cost Metrics

## Risks

### Modules become tightly coupled

Probability: High

Impact: High

Mitigation: Enforce boundaries via code reviews and architecture tests

### Monolith becomes too large to extract

Probability: Medium

Impact: High

Mitigation: Set clear extraction triggers and review every 6 months

### Performance bottleneck in single deployment

Probability: Low

Impact: Medium

Mitigation: Horizontal scaling of the entire monolith is still possible

### Team resists migration to microservices later

Probability: Medium

Impact: Medium

Mitigation: Document migration plan and get team buy-in now

## Related Decisions