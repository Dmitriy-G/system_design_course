## Availability

### Definitions

Availability is the ability to complete the process within the specified time to process the order and payment. Payment processing time should not exceed one minute from the time the customer initiates the payment process until it is completed.

### Goals

Total availability: 99.9


Allowed downtime / per month: 43 mins

### Measure method

A = MTBF / (MTBF + MTTR)

A = 7,200 / (7,200 + 30) = 99.58%

## Latency

### Definitions

Measured at the UX level. This includes page rendering, HTTP requests between services, JDBC requests, HTTP requests to external APIs, and switching to a reserved API when the primary payment gateway is unavailable.

### Goals

Click on the Order button and show details form: 

P95 - 0.5 ms

Click on the Pay button and move to paid screen:

P95 - 1 sec

### Measure method

P95 = sum(rate(http_server_requests_seconds_bucket[5m])) by (le)

## Reliability / Incidents

### Definitions

The successful order processing rate. Count only orders that was failed by technical reason

### Goals

The successful orders rate per day: 95%

MTBF: 43000 min

### Measure method

R = Euler's number (2.718) ^ (the time period in minutes / MTBF)

R = 2.718^(-1,440 / 43000)

R = 2.718^(-0.033)

R = 0.9675 = 96.75%


## Maintainability

### Definitions

Time to fix problem and delivery to production

### Goals

Success deployments: 96%

Test coverage: 85%

MTTR: 30 mins

### Measure Method

D = (Successful deployments / Total deployment attempts) × 100

D = (48 / 50) × 100 = 96%

C = (Lines of code covered by tests / Total lines of code) × 100

C = (8,500 / 10,000) × 100 = 85%

MTTR = Total downtime / Number of incidents

MTTR = 150 / 5 = 30

## Scalability

### Definitions

The ability to endure the spike activity and ability to scaling in future

### Goals

Total orders per day: 400000

RPS_Avg: 4.63

RPS_Peak: 18.52

Concurrency_Avg: 2.31

Concurrency_Peak: 9.26

Concurrency_Safe: 18.52

### Measure Method

Total orders per day = DAU × orders per user = 2000000 * 0.2 = 400000

RPS_Avg = Total orders per day / Seconds in a day = = 400000 / 86400 = 4.63

20% of time = 20% × 86,400 = 17280

Peak orders = 400,000 × 0.8 = 320000

RPS_Peak = Peak orders / Peak window seconds = 320000 / 17280 = 18.52

Concurrency_Avg = Avg_RPS * Avg_Latency = 4.63 * 0.5 = 2.31

Concurrency_Peak = Avg_Peak * Avg_Latency = 18.52 * 0.5 = 9.26

Concurrency_Safe = Concurrency_Peak * safe factor = 9.26 * 2 = 18.52

## Security

### Definitions

Ability to adjust responses to security incidents in runtime. A comparison with real incidents will be made after the fact.

### Goals

Unauthorized access blocking rate: 99.99%

Fraud detection rate: 99.9%

### Measure Method

B = (Blocked unauthorized requests / Total unauthorized attempts) × 100

B = (9999 / 10000) × 100 = 99.99%

F = (Orders correctly flagged as fraudulent / Total fraudulent orders) × 100

F = (999 / 1000) × 100 = 99.9%

## Network Throughput

### Definitions

The ingress/egress traffic

### Goals

Ingress Avg: 4.63 kb/sec

Ingress Peak: 18.52 kb/sec

Egress Avg: 9.26 kb/sec

Egress Peak: 37.04 kb/sec

Total traffic Avg: 13.89 kb/sec

Total traffic Peak: 55.56 kb/sec

### Measure Method

Ingress Avg = RPS_Avg * payload size = 4.63 * 1 = 4.63 kb/sec

Ingress Peak = RPS_Peak * payload size = 18.52 * 1 = 18.52 kb/sec

Egress Avg = RPS_Avg * response size = 4.63 * 2 = 9.26 kb/sec

Egress Peak = RPS_Peak * response size = 18.52 * 2 = 37.04 kb/sec

Total Traffic Avg = Ingress Avg + Egress Avg = 13.89 kb/sec

Total Traffic Peak = Ingress Peak + Egress Peak = 55.56 kb/sec

## Integrations

### Definitions

Integration with payment gateway and costs for it. We have a cheap provider (0.05$ per request) and more expensive (0.50$ per request).

### Goals

Request to main payments provider per day: 90%

Request to main payments provider in a peak time: 50%

Total costs for all providers per day: 250$

### Measure Method

R = (Main provider requests / Total requests) * 100

R = (900 / 1000) * 100 = 90%

C = (Requests to main provider * 0.05) + (requests to reserve provider * 0.50)

C = (600 * 0.50) + (400 * 0.50) = 230$