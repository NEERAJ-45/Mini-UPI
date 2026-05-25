# RevPay: AWS-Native Observability & Traffic — Free Tier Edition

> **Migration**: Prometheus + Grafana + Nginx + Zipkin → CloudWatch + X-Ray + ALB
> **Constraint**: Must stay within **AWS Free Tier** (no charges)
> **Audience**: Beginner to AWS — educational notes included throughout

---

## AWS Free Tier Budget — Hard Limits

> ⚠️ **CAUTION**: If you exceed ANY of these limits, AWS will charge your card. The plan below is engineered to stay safely inside all of them. Set up a **Billing Alarm** first (it's free) before touching anything else.

| AWS Service | Free Tier Allowance | Our Budget | Headroom |
|---|---|---|---|
| **CloudWatch Metrics** | 10 custom metrics | 10 | 0 (tight) |
| **CloudWatch Alarms** | 10 alarms | 10 | 0 (tight) |
| **CloudWatch Dashboards** | 3 dashboards (50 metrics each) | 3 | 0 (tight) |
| **CloudWatch Logs** | 5 GB ingestion + 5 GB storage/month | ~2 GB | 3 GB buffer |
| **CloudWatch API** | 1,000,000 requests/month | ~200k | 800k buffer |
| **X-Ray** | 100,000 traces recorded/month | ~50k | 50k buffer |
| **X-Ray** | 1,000,000 traces scanned/month | ~100k | 900k buffer |
| **ALB** | 750 hours + 15 LCUs/month *(12 months only)* | 1 ALB | ✅ |
| **SNS** | 1,000 email notifications/month | ~200 | 800 buffer |
| **EC2** | 750 hours t2.micro/month *(12 months only)* | As needed | — |

> **IMPORTANT**: The ALB and EC2 free tiers are **12-month only** (from account creation). CloudWatch, X-Ray, and SNS free tiers are **permanent**. If your AWS account is older than 12 months, the ALB section will cost ~$16-22/month.

---

## What Each AWS Service Does (Beginner Guide)

| AWS Service | What It Replaces | One-Line Explanation |
|---|---|---|
| **CloudWatch Metrics** | Prometheus | Stores numbers over time (like "how many requests per second?"). You push metrics, it graphs them. |
| **CloudWatch Logs** | `docker logs` / log files | A centralized place where ALL your services send their log lines. You can search across them. |
| **CloudWatch Log Insights** | `grep` on log files | A query language to search logs. Like SQL but for logs. "Show me all errors in the last hour." |
| **CloudWatch Dashboards** | Grafana | Visual panels (graphs, numbers, charts) that show your metrics. You build them in the AWS console. |
| **CloudWatch Alarms** | Grafana alerts | "If error rate goes above 10%, send me an email." Watches a metric and triggers an action. |
| **AWS X-Ray** | Zipkin | Traces a single request as it travels through Gateway → Transaction → Wallet → Kafka → Notification. Shows you a timeline. |
| **CloudWatch Agent** | Prometheus Node Exporter | A small program that runs on your server and sends CPU/memory/disk metrics to CloudWatch. |
| **SNS (Simple Notification)** | Email / Slack webhook | A message bus. Alarms send messages to SNS, SNS sends them to your email/phone/Slack. |
| **ALB (Application Load Balancer)** | Nginx reverse proxy | Sits in front of your services, distributes traffic, does health checks, handles HTTPS. Layer 7 (understands HTTP). |
| **NLB (Network Load Balancer)** | — | Like ALB but for raw TCP connections (databases, Kafka). Layer 4. Faster, dumber. |
| **ACM (Certificate Manager)** | Let's Encrypt / self-signed certs | Free SSL certificates for your ALB. Auto-renews. |

### How They Connect (The Big Picture)

```
                    ┌─────────────────────────────────────────────┐
                    │              AWS Cloud                       │
                    │                                             │
 User Request ──────►  ALB (replaces Nginx)                      │
                    │    ├── /auth/**    → API Gateway :8080      │
                    │    ├── /wallet/**  → API Gateway :8080      │
                    │    └── /txn/**     → API Gateway :8080      │
                    │                                             │
                    │  Each service sends:                        │
                    │    ├── Metrics ────► CloudWatch Metrics      │
                    │    ├── Logs ───────► CloudWatch Logs         │
                    │    └── Traces ─────► AWS X-Ray               │
                    │                                             │
                    │  CloudWatch:                                │
                    │    ├── Dashboards (you view graphs here)    │
                    │    ├── Alarms ────► SNS ────► Your Email    │
                    │    └── Log Insights (search logs here)      │
                    │                                             │
                    └─────────────────────────────────────────────┘
```

---

## 1. Current State → Target State

### What Gets Removed from `docker-compose.yml`

| Service | Lines | Reason |
|---|---|---|
| `nginx` | L108-125 | Replaced by ALB |
| `nginx-exporter` | L130-141 | Replaced by ALB native metrics |
| `prometheus` | L146-160 | Replaced by CloudWatch Metrics |
| `grafana` | L166-182 | Replaced by CloudWatch Dashboards |
| `zipkin` | L187-194 | Replaced by AWS X-Ray |

### What Gets Removed from `pom.xml`

| Dependency | Lines | Replacement |
|---|---|---|
| `micrometer-registry-prometheus` | L132-135 | `micrometer-registry-cloudwatch2` |
| *(future)* `opentelemetry-exporter-zipkin` | Per tracing guide | `aws-xray-recorder-sdk-spring` |

### What Stays Exactly The Same

- ✅ Spring Boot Actuator (already in parent POM L128-131)
- ✅ Micrometer core API (just swap the *registry* backend)
- ✅ All custom metric code patterns (`Counter.builder(...)`, `Timer.builder(...)`)
- ✅ Spring Cloud Gateway as the application-layer API gateway
- ✅ Kafka, PostgreSQL, Redis infrastructure
- ✅ Resilience4j circuit breaker config

---

## 2. Proposed Changes

---

### Component 1: Centralized Structured Logging

#### What Changes

Right now your services log plain text to stdout. We change them to log **structured JSON** and ship those logs to **CloudWatch Logs** (grouped by service).

#### Log Group Convention

```
/revpay/{environment}/{service-name}
```

| Log Group | Retention | Why This Retention |
|---|---|---|
| `/revpay/dev/api-gateway` | 7 days | Low value, save storage |
| `/revpay/dev/user-service` | 7 days | Low value |
| `/revpay/dev/wallet-service` | 7 days | Low value |
| `/revpay/dev/transaction-service` | 14 days | Audit trail for payments |
| `/revpay/dev/notification-service` | 3 days | Very high volume, low value |

#### Structured Log Format

Every log line becomes a JSON object (instead of a plain string):

```json
{
  "timestamp": "2026-05-24T01:22:04.123Z",
  "level": "INFO",
  "service": "transaction-service",
  "traceId": "1-abc123-def456",
  "message": "Payment initiated",
  "userId": "user-42",
  "path": "/transactions/send",
  "amount": 500.00,
  "sagaState": "PENDING"
}
```

**Why JSON?** → CloudWatch Log Insights can query JSON fields directly. With plain text, you'd need regex to extract data.

#### Log Insights Query Library

These are pre-built "saved queries" you run in the CloudWatch console:

```sql
-- Find all errors in the last hour
fields @timestamp, traceId, message
| filter level = "ERROR"
| sort @timestamp desc
| limit 50

-- Track a single payment across all services (paste a traceId)
fields @timestamp, service, message, sagaState
| filter traceId = "PASTE-YOUR-TRACE-ID-HERE"
| sort @timestamp asc

-- Find stuck sagas (pending for too long)
fields @timestamp, traceId, message
| filter sagaState = "PENDING" and service = "transaction-service"
| sort @timestamp desc

-- Outbox publishing failures
fields @timestamp, traceId, message
| filter service = "user-service" and message like /outbox/ and level = "ERROR"
```

#### TODO Stubs

- [ ] Add `logstash-logback-encoder` dependency to parent POM
- [ ] Create `logback-spring.xml` per service for JSON output
- [ ] Create CloudWatch log groups (via console or Terraform)
- [ ] Create `docs/monitoring/LOG_INSIGHTS_QUERIES.md` with saved queries

---

### Component 2: Metrics & CloudWatch Dashboards

#### Free Tier Constraint: Only 10 Custom Metrics

**WARNING**: Prometheus let you have unlimited metrics. CloudWatch Free Tier gives you only **10 custom metrics total** across all services. We must choose the 10 most important ones.

#### The 10 Custom Metrics (Carefully Chosen)

| # | Metric Name | Namespace | Type | Service | Why This Made the Cut |
|---|---|---|---|---|---|
| 1 | `PaymentSuccessCount` | `RevPay/Transactions` | Counter | Transaction | Core business KPI |
| 2 | `PaymentFailureCount` | `RevPay/Transactions` | Counter | Transaction | Core business KPI |
| 3 | `PaymentLatencyP99` | `RevPay/Transactions` | Timer | Transaction | SLA indicator |
| 4 | `SagaPendingCount` | `RevPay/Transactions` | Gauge | Transaction | Stuck saga detector |
| 5 | `IdempotencyCacheHits` | `RevPay/Transactions` | Counter | Transaction | Duplicate request visibility |
| 6 | `OutboxPendingCount` | `RevPay/UserService` | Gauge | User | Outbox health |
| 7 | `WalletTransferLatency` | `RevPay/Wallet` | Timer | Wallet | Performance bottleneck detector |
| 8 | `RateLimitRejections` | `RevPay/Gateway` | Counter | Gateway | Traffic pressure indicator |
| 9 | `HttpErrors5xx` | `RevPay/System` | Counter | All | System-wide error indicator |
| 10 | `ActiveRequests` | `RevPay/System` | Gauge | All | Current load indicator |

**What about JVM, HikariCP, Kafka metrics?** → Those come from Spring Boot Actuator as **built-in metrics** and are available via the `/actuator/metrics` endpoint for local debugging. We don't push them to CloudWatch (they'd eat our free tier). You can view them locally or add them later if you move to a paid tier.

#### Metric Namespace Convention

```
RevPay/{Category}
```

Only a few namespaces (keeps it clean):
- `RevPay/Transactions` — payment business metrics
- `RevPay/System` — cross-cutting system metrics
- `RevPay/Gateway`, `RevPay/Wallet`, `RevPay/UserService` — service-specific

#### 3 Dashboards (Free Tier Max)

**Dashboard 1: `RevPay-Business`** (The money dashboard)
- Payment TPS: success vs failure — time-series graph
- Payment latency P99 — time-series graph
- Saga pending count — single number widget (red if > 10)
- Idempotency cache hit count — single number widget
- Total payments today — single number widget

**Dashboard 2: `RevPay-System`** (Is anything broken?)
- HTTP 5xx error rate — time-series graph
- Active requests — time-series graph
- Rate limiter rejections — time-series graph
- Outbox pending count — single number widget (red if > 50)
- Wallet transfer latency — time-series graph
- Alarm status widget — shows all 10 alarms in one panel

**Dashboard 3: `RevPay-ALB`** (Traffic & health — uses *free* ALB built-in metrics, not our 10)
- ALB request count — time-series
- ALB target response time — time-series
- Healthy vs unhealthy targets — number widgets
- HTTP status distribution (2xx/4xx/5xx) — stacked area
- Active connections — time-series

> **TIP**: ALB metrics are **automatically published** by AWS under the `AWS/ApplicationELB` namespace. They do NOT count against your 10 custom metric limit. Same for EC2 metrics under `AWS/EC2`. Free bonus data!

#### TODO Stubs

- [ ] Swap `micrometer-registry-prometheus` → `micrometer-registry-cloudwatch2` in parent POM
- [ ] Configure CloudWatch metrics export in all 5 `application.yml` files
- [ ] Implement the 10 custom metrics in service code
- [ ] Build 3 dashboards in CloudWatch console, export JSON to `infra/aws/cloudwatch/dashboards/`

---

### Component 3: Distributed Tracing (X-Ray)

#### Free Tier Constraint: 100,000 Traces/Month

At 5K TPS, you'd generate 432 million traces/month at 100% sampling. We need **aggressive sampling** to stay under 100k.

#### Sampling Strategy

| Environment | Rate | Reservoir | Monthly Estimate | Within Free Tier? |
|---|---|---|---|---|
| `dev` (local) | 100% | — | ~1,000 (manual testing) | ✅ Way under |
| `dev` (deployed) | 5% | 1/sec | ~50,000 | ✅ Under |
| `prod` | 1% | 1/sec | ~90,000 | ✅ Tight but safe |
| Errors (always) | 100% | — | Included above | ✅ |

> **Reservoir** means "always capture at least 1 trace per second regardless of the percentage." This guarantees you never have a 0-trace minute, even at 1%.

#### Trace Correlation Strategy

```
User sends POST /transactions/send
  → ALB adds header: X-Amzn-Trace-Id: Root=1-abc123-def456
    → API Gateway reads header, creates segment
      → Transaction Service creates subsegment
        → Feign call to Wallet Service (header auto-propagated)
        → Kafka publish (trace ID injected into Kafka record headers)
          → Notification Service extracts trace from Kafka headers
```

**The trace ID flows through every service.** In X-Ray console, you click one trace and see the full timeline:

```
[api-gateway]         ████████████████████████████████ 150ms
  [transaction-svc]     ████████████████████████████ 120ms
    [wallet-svc]             █████████████████ 60ms
  [notification-svc]                    ████ 20ms (async/Kafka)
```

#### Trace Annotations (Business Context)

| Key | Example Value | Purpose |
|---|---|---|
| `paymentId` | `txn-abc-123` | Find trace by payment ID |
| `userId` | `user-42` | Find all traces for a user |
| `sagaState` | `SUCCESS` | Filter by outcome |
| `amount` | `500.00` | Business context |

#### TODO Stubs

- [ ] Add AWS X-Ray SDK dependencies to parent POM
- [ ] Remove Zipkin/OTel dependencies from parent POM
- [ ] Create `XRayConfig.java` TODO stub in each service
- [ ] Configure X-Ray sampling rules per environment
- [ ] Remove Zipkin service from docker-compose

---

### Component 4: Alerting Strategy

#### Free Tier Constraint: Only 10 Alarms

#### Severity Levels

| Severity | Meaning | Notification | Response |
|---|---|---|---|
| **SEV1** | System broken, payments failing | Email (immediate) | Drop everything |
| **SEV2** | System degraded, investigate soon | Email | Within 1 hour |
| **SEV3** | Warning, could become a problem | Email (batched) | Within 1 day |

#### The 10 Alarms

| # | Alarm Name | Metric | Condition | Severity |
|---|---|---|---|---|
| 1 | `revpay-payment-failures-critical` | PaymentFailureCount | > 10 in 5 min | SEV1 |
| 2 | `revpay-5xx-errors-critical` | HttpErrors5xx | > 20 in 5 min | SEV1 |
| 3 | `revpay-latency-high` | PaymentLatencyP99 | > 2000ms for 5 min | SEV2 |
| 4 | `revpay-saga-stuck` | SagaPendingCount | > 50 for 10 min | SEV2 |
| 5 | `revpay-outbox-backlog` | OutboxPendingCount | > 100 for 10 min | SEV2 |
| 6 | `revpay-rate-limit-surge` | RateLimitRejections | > 100 in 5 min | SEV2 |
| 7 | `revpay-alb-unhealthy` | ALB: UnHealthyHostCount | > 0 for 2 min | SEV1 |
| 8 | `revpay-alb-5xx` | ALB: HTTPCode_ELB_5XX | > 20 in 5 min | SEV1 |
| 9 | `revpay-idempotency-surge` | IdempotencyCacheHits | > 500 in 5 min | SEV3 |
| 10 | `revpay-billing` | AWS/Billing: EstimatedCharges | > $1 | SEV1 |

> **IMPORTANT**: **Alarm #10 is the most important one.** Set this up FIRST. It alerts you if AWS charges exceed $1, so you never get a surprise bill.

#### SNS Setup (Email Notifications)

```
SNS Topic: "revpay-alerts"
  └── Subscription: your-email@gmail.com (email)
```

All 10 alarms → same SNS topic → your email. Keep it simple.

#### TODO Stubs

- [ ] Create SNS topic `revpay-alerts` + email subscription
- [ ] Create all 10 CloudWatch alarms (Terraform stubs)
- [ ] Test: trigger an alarm manually, verify email notification

---

### Component 5: Load Balancing (ALB)

#### Free Tier Constraint: 1 ALB, 750 hours/month

We use **one single ALB** for everything. The ALB replaces Nginx as the entry point.

#### Architecture

```
Internet
  │
  ▼
┌─────────────────────────────────────────┐
│  ALB (Application Load Balancer)        │
│  ├── Listener: HTTP :80                 │
│  │   └── Redirect → HTTPS :443         │
│  └── Listener: HTTPS :443              │
│       ├── Rule: /* → TG: api-gateway    │
│       └── Health check: /actuator/health│
└──────────────┬──────────────────────────┘
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
 Gateway    Gateway    Gateway
 (AZ-a)    (AZ-b)    (AZ-c)
 :8080     :8080     :8080
```

> **Why route everything to the API Gateway instead of individual services?**
> Your Spring Cloud Gateway already handles JWT validation, rate limiting, and routing. We don't duplicate that in ALB rules. ALB's job is just: SSL termination, health checks, and distributing traffic across Gateway instances.

#### What ALB Replaces from `nginx.conf`

| Nginx Feature | ALB Equivalent |
|---|---|
| `upstream api_gateway { least_conn; }` | ALB target group with "least outstanding requests" routing |
| `limit_req zone=upi_limit` | Keep in Spring Cloud Gateway (ALB doesn't do app-level rate limiting) |
| `proxy_pass http://api_gateway` | ALB listener rule → target group |
| `proxy_set_header X-Real-IP` | ALB automatically adds `X-Forwarded-For` |
| `/health` endpoint | ALB health check → `/actuator/health` |
| `/nginx_status` for Prometheus | Gone — ALB publishes its own metrics to CloudWatch automatically |
| SSL? (not configured in nginx) | ACM certificate on ALB (free!) |

#### SSL Termination Strategy

```
ACM (free cert) ──► ALB terminates HTTPS
                      │
                      ▼ (plain HTTP inside VPC)
                   API Gateway :8080
```

- ACM provides **free** SSL certificates (auto-renewed)
- ALB handles TLS termination (no CPU overhead on your Java apps)
- Traffic inside VPC is plain HTTP (secure because it's private network)

#### Health Check Config

| Setting | Value | Why |
|---|---|---|
| Path | `/actuator/health` | Already exists on all services |
| Protocol | HTTP | Internal VPC traffic |
| Interval | 30s | Free tier friendly (fewer health check requests) |
| Healthy threshold | 3 | Confirm healthy 3 times before routing traffic |
| Unhealthy threshold | 2 | Mark unhealthy after 2 consecutive failures |
| Timeout | 5s | Generous for Spring Boot cold starts |

#### Failover & Scaling Visibility

- **Multi-AZ**: ALB automatically spans 2+ AZs
- **Deregistration delay**: 30s (in-flight requests complete before removal)
- **Metrics (free)**: ALB auto-publishes RequestCount, TargetResponseTime, HealthyHostCount, etc. to CloudWatch under `AWS/ApplicationELB` — these are NOT counted in your 10 custom metrics

#### NLB Decision

> **We skip NLB for now.** NLB is for raw TCP load balancing (databases, Kafka). In development, your services connect directly to PostgreSQL/Redis/Kafka by hostname. NLB only matters when you have multiple database replicas or Kafka brokers to balance across in production. Add it later if needed.

#### TODO Stubs

- [ ] Deploy ALB via Terraform or AWS Console
- [ ] Configure ALB target group → API Gateway
- [ ] Configure ACM certificate for SSL
- [ ] Remove `nginx` + `nginx-exporter` from docker-compose
- [ ] Delete `infra/nginx/` directory (after ALB is verified working)

---

## 3. Operational Debugging Workflows

### Workflow 1: "Payments Are Failing!" (SEV1)

```
📧 Email alarm → Open Dashboard: RevPay-Business
  → See failure count spiking
    → Open CloudWatch Logs → /revpay/dev/transaction-service
      → Run Log Insights: filter level='ERROR', last 15min
        → Copy a traceId from the error
          → Open X-Ray → Search by traceId
            → See which service/call failed in the timeline
              → Fix: DB issue? Wallet timeout? Bug in code?
```

### Workflow 2: "Sagas Are Stuck" (SEV2)

```
📧 Email alarm → Dashboard: RevPay-Business → SagaPendingCount
  → Log Insights: filter sagaState='PENDING' AND age > 5 minutes
    → Get traceIds of stuck sagas
      → X-Ray: trace each one
        → Where did it stall?
          → Wallet call never returned? Kafka publish failed? Compensation loop?
```

### Workflow 3: "Is It Slow?" (Latency Investigation)

```
📧 Email alarm → Dashboard: RevPay-Business → PaymentLatencyP99
  → X-Ray: filter traces with response time > 2s
    → Look at subsegment breakdown
      → Where is the time spent?
        → DB query? Feign call? GC pause? Redis?
```

---

## 4. Retention & Cost Optimization

| Resource | Setting | Monthly Cost |
|---|---|---|
| Log retention: transaction-service | 14 days | Free (within 5 GB) |
| Log retention: all other services | 3-7 days | Free (within 5 GB) |
| Custom metrics | 10 (hard limit) | Free |
| Alarms | 10 (hard limit) | Free |
| Dashboards | 3 (hard limit) | Free |
| X-Ray traces | 1% sampling + 1/sec reservoir | Free (within 100k) |
| SNS email notifications | ~200/month | Free (within 1,000) |
| ALB | 1 instance, 750 hrs | Free (12 months) |
| **Total** | | **$0.00** |

**Cost Guardrails:**
1. Billing alarm (#10) fires at $1 threshold
2. Log groups have explicit retention policies (auto-delete old logs)
3. X-Ray sampling at 1% in prod
4. Only 10 custom metrics (no cardinality explosion)
5. No NLB (saves $16/month)

---

## 5. Phased Implementation Plan

### Phase 1: Billing Alarm + Logging (Week 1)

- [ ] Create AWS account (if not done) and set up IAM user
- [ ] **FIRST THING**: Create billing alarm (Alarm #10: charges > $1)
- [ ] Create SNS topic `revpay-alerts` + subscribe your email
- [ ] Add `logstash-logback-encoder` dependency to parent POM
- [ ] Create `logback-spring.xml` for each service (structured JSON)
- [ ] Create CloudWatch log groups (via console or Terraform stub)
- [ ] Configure CloudWatch Logs agent or SDK to ship logs
- [ ] Verify: logs appear in CloudWatch console
- [ ] Create `docs/monitoring/LOG_INSIGHTS_QUERIES.md` with saved queries

### Phase 2: Metrics + Dashboards (Week 2)

- [ ] Add `micrometer-registry-cloudwatch2` to parent POM (keep prometheus too — dual-write)
- [ ] Configure CloudWatch metrics export in all 5 `application.yml` files
- [ ] Implement the 10 custom metrics in service code (TODO stubs)
- [ ] Verify: metrics appear in CloudWatch console under `RevPay/*` namespaces
- [ ] Build Dashboard 1: `RevPay-Business` (in console, export JSON)
- [ ] Build Dashboard 2: `RevPay-System` (in console, export JSON)
- [ ] Create all 10 CloudWatch alarms
- [ ] Test: trigger an alarm manually, verify email notification received
- [ ] Save dashboard JSON files to `infra/aws/cloudwatch/dashboards/`

### Phase 3: Tracing + ALB (Week 3-4)

- [ ] Add AWS X-Ray SDK dependencies to parent POM
- [ ] Create `XRayConfig.java` TODO stub in each service
- [ ] Configure X-Ray sampling rules (1% prod, 100% dev)
- [ ] Remove Zipkin/OTel dependencies from POM
- [ ] Remove Zipkin service from docker-compose
- [ ] Verify: traces appear in X-Ray console
- [ ] Deploy ALB via Terraform or AWS Console
- [ ] Configure ALB target group → API Gateway
- [ ] Configure ACM certificate for SSL
- [ ] Build Dashboard 3: `RevPay-ALB` (uses free ALB metrics)

### Phase 4: Cleanup + Docs (Week 5)

- [ ] Remove `micrometer-registry-prometheus` from parent POM
- [ ] Remove prometheus/zipkin config from all `application.yml`
- [ ] Remove prometheus, grafana, nginx, nginx-exporter, zipkin from docker-compose
- [ ] Archive `infra/prometheus/`, `infra/grafana/`, `infra/nginx/` → `infra/_archived/`
- [ ] Update monitoring docs
- [ ] Create `docs/monitoring/OPERATIONAL_RUNBOOKS.md` with debugging workflows
- [ ] Final end-to-end smoke test

---

## 6. Service-Wise Monitoring Checklist

### API Gateway (port 8080)

| Item | Status | Custom Metric? |
|---|---|---|
| Structured JSON logging → CloudWatch Logs | `TODO` | — |
| CloudWatch metric: RateLimitRejections | `TODO` | ✅ #8 of 10 |
| X-Ray tracing filter | `TODO` | — |
| ALB target group member | `TODO` | — |
| Health check: `/actuator/health` | ✅ Done | — |

### User Service (port 8081)

| Item | Status | Custom Metric? |
|---|---|---|
| Structured JSON logging → CloudWatch Logs | `TODO` | — |
| CloudWatch metric: OutboxPendingCount | `TODO` | ✅ #6 of 10 |
| X-Ray tracing filter | `TODO` | — |
| Health check: `/actuator/health` | ✅ Done | — |

### Wallet Service (port 8082)

| Item | Status | Custom Metric? |
|---|---|---|
| Structured JSON logging → CloudWatch Logs | `TODO` | — |
| CloudWatch metric: WalletTransferLatency | `TODO` | ✅ #7 of 10 |
| X-Ray tracing filter | `TODO` | — |
| X-Ray SQL subsegment interceptor | `TODO` | — |
| Health check: `/actuator/health` | ✅ Done | — |

### Transaction Service (port 8083)

| Item | Status | Custom Metric? |
|---|---|---|
| Structured JSON logging → CloudWatch Logs | `TODO` | — |
| CloudWatch metric: PaymentSuccessCount | `TODO` | ✅ #1 of 10 |
| CloudWatch metric: PaymentFailureCount | `TODO` | ✅ #2 of 10 |
| CloudWatch metric: PaymentLatencyP99 | `TODO` | ✅ #3 of 10 |
| CloudWatch metric: SagaPendingCount | `TODO` | ✅ #4 of 10 |
| CloudWatch metric: IdempotencyCacheHits | `TODO` | ✅ #5 of 10 |
| X-Ray tracing filter | `TODO` | — |
| X-Ray SQL subsegment interceptor | `TODO` | — |
| Health check: `/actuator/health` | ✅ Done | — |

### Notification Service (port 8084)

| Item | Status | Custom Metric? |
|---|---|---|
| Structured JSON logging → CloudWatch Logs | `TODO` | — |
| X-Ray trace extraction from Kafka headers | `TODO` | — |
| Health check: `/actuator/health` | ✅ Done | — |

> Notification Service has no custom metrics assigned (we used all 10 on higher-priority services). Monitor it via logs and X-Ray traces only.

---

## 7. New File System Layout

```
infra/aws/                          ← NEW directory
├── cloudwatch/
│   ├── log-groups.tf               ← 5 log group definitions
│   ├── alarms.tf                   ← 10 alarm definitions
│   └── dashboards/
│       ├── revpay-business.json    ← Dashboard 1
│       ├── revpay-system.json      ← Dashboard 2
│       └── revpay-alb.json         ← Dashboard 3
├── xray/
│   └── sampling-rules.json         ← Sampling config per environment
├── alb/
│   ├── main.tf                     ← ALB + listener + target group
│   ├── security-groups.tf          ← Network rules
│   └── acm.tf                      ← SSL certificate (TODO stub)
└── sns/
    └── alerts-topic.tf             ← SNS topic + email subscription

docs/monitoring/
├── AWS_OBSERVABILITY_MIGRATION_PLAN.md  ← THIS FILE
├── DEV_PLAN.md                          ← Existing (will be updated)
├── LOG_INSIGHTS_QUERIES.md              ← NEW
└── OPERATIONAL_RUNBOOKS.md              ← NEW
```

### Files to Modify

| File | Change Summary |
|---|---|
| `pom.xml` | Swap prometheus → cloudwatch2 registry, add X-Ray SDK |
| `docker-compose.yml` | Remove 5 services (nginx, nginx-exporter, prometheus, grafana, zipkin) |
| All 5 `application.yml` files | CloudWatch metrics config, remove prometheus/zipkin config |
| All 5 services | Add `logback-spring.xml`, add `XRayConfig.java` TODO stub |

### Archived (Phase 4)

| Directory | Status |
|---|---|
| `infra/prometheus/` | Archive → `infra/_archived/prometheus/` |
| `infra/grafana/` | Archive → `infra/_archived/grafana/` |
| `infra/nginx/` | Archive → `infra/_archived/nginx/` |

---

## 8. Final Smoke Test

```
1. docker-compose up -d                          → only infra containers start
2. Start all 5 Spring Boot services
3. POST /auth/register (create user)             → log appears in CloudWatch
4. POST /transactions/send (make payment)         → trace appears in X-Ray
5. Check Dashboard 1                              → TPS counter incremented
6. Stop wallet-service                            → ALB marks target unhealthy
7. Check email                                    → alarm notification received
8. Check AWS Billing console                      → $0.00
```
