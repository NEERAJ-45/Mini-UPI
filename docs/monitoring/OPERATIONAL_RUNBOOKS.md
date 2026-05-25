# 🚨 Operational Runbooks — RevPay Incident Response

Step-by-step debugging workflows for production incidents.
Each runbook maps an **alarm** → **investigation steps** → **resolution actions**.

---

## Runbook 1: Payment Failures (SEV1)

**Trigger**: Alarm `revpay-payment-failures-critical` — PaymentFailureCount > 10 in 5 min

### Step 1: Assess Scope
1. Open **Dashboard: RevPay-Business**
2. Check: Is failure count spiking or sustained?
3. Check: Is success count also dropping? (total outage vs partial)
4. Check: Is latency also high? (could be timeouts causing failures)

### Step 2: Find the Errors
1. Open **CloudWatch Logs → Log Insights**
2. Select log group: `/revpay/dev/transaction-service`
3. Run query:
   ```
   fields @timestamp, traceId, message, metadata.errorCode
   | filter level = "ERROR"
   | sort @timestamp desc
   | limit 30
   ```
4. Note the **error pattern**: Is it the same error repeating? Different errors?

### Step 3: Trace a Failure
1. Copy a `traceId` from the error logs
2. Open **X-Ray Console → Traces → Search by Trace ID**
3. Look at the timeline — which subsegment is red (failed)?
   - **Wallet Service red** → Wallet is down or returning errors
   - **Database subsegment red** → PostgreSQL connection issue
   - **Redis subsegment red** → Redis/Idempotency issue
   - **Kafka subsegment red** → Kafka broker issue

### Step 4: Check Downstream Health
1. Open **Dashboard: RevPay-ALB** → Are all targets healthy?
2. SSH into EC2: `docker ps` → Are all containers running?
3. Check: `docker logs upi-transaction-service --tail 50`

### Step 5: Resolution Actions
| Root Cause | Action |
|---|---|
| Wallet container down | `docker restart upi-wallet-service` |
| PostgreSQL connection pool exhausted | Restart service, check for connection leaks |
| Redis unreachable | `docker restart upi-redis`, check memory |
| Kafka broker down | `docker restart upi-kafka`, check Zookeeper |
| Application bug | Rollback to previous Docker image version |

---

## Runbook 2: High Latency (SEV2)

**Trigger**: Alarm `revpay-latency-high` — PaymentLatencyP99 > 2000ms for 5 min

### Step 1: Identify Slow Endpoints
1. Open **Dashboard: RevPay-Business** → Latency P99 panel
2. Is it all endpoints or specific ones?

### Step 2: Trace Slow Requests
1. Open **X-Ray Console**
2. Filter: `responsetime > 2` (seconds)
3. Open a slow trace → look at the subsegment durations
4. Identify the **longest subsegment**:
   - `wallet-service` subsegment = Feign call bottleneck
   - `PostgreSQL` subsegment = Slow DB queries
   - `Redis` subsegment = Redis latency
   - Large gap between subsegments = GC pause or thread starvation

### Step 3: DB-Specific Investigation
If PostgreSQL is the bottleneck:
1. SSH into EC2
2. `docker exec -it upi-postgres psql -U upi_admin -d upi_transactions`
3. Check active queries:
   ```sql
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
   FROM pg_stat_activity
   WHERE state != 'idle'
   ORDER BY duration DESC;
   ```
4. Check for lock waits:
   ```sql
   SELECT * FROM pg_locks WHERE NOT granted;
   ```

### Step 4: JVM-Specific Investigation
If GC pauses suspected:
1. Check `/actuator/metrics/jvm.gc.pause` on the service endpoint
2. If GC pause time is high → increase container heap size (`-Xmx`)
3. Check `/actuator/metrics/jvm.memory.used` → is heap near max?

### Resolution Actions
| Root Cause | Action |
|---|---|
| Slow DB queries | Add indexes, optimize queries, check `EXPLAIN ANALYZE` |
| Connection pool exhaustion | Increase HikariCP `maximumPoolSize` |
| Optimistic lock retries | Expected under load; check retry backoff config |
| GC pauses | Increase `-Xmx`, consider G1GC tuning |
| Network latency | Check EC2 instance type, consider placement groups |

---

## Runbook 3: Stuck Sagas (SEV2)

**Trigger**: Alarm `revpay-saga-stuck` — SagaPendingCount > 50 for 10 min

### Step 1: Find Stuck Sagas
1. Open **CloudWatch Logs → Log Insights**
2. Select: `/revpay/dev/transaction-service`
3. Run query:
   ```
   fields @timestamp, traceId, metadata.paymentId, sagaState, message
   | filter sagaState = "PENDING"
   | sort @timestamp asc
   | limit 50
   ```
4. Note: How old are the oldest PENDING entries?

### Step 2: Trace Each Stuck Saga
1. Pick the oldest PENDING saga's `traceId`
2. Open **X-Ray → Search by Trace ID**
3. Check: Where did the saga stall?
   - **No wallet subsegment** → Feign call to wallet never happened (circuit breaker open?)
   - **Wallet subsegment with no response** → Wallet call timed out
   - **Kafka publish subsegment failed** → Outbox event wasn't published

### Step 3: Check Circuit Breaker
1. Call: `GET http://{ec2-ip}:8083/actuator/circuitbreakers`
2. If `walletService` state is `OPEN` → Wallet service was failing, circuit breaker tripped
3. Fix wallet service → circuit breaker will auto-close after `wait-duration-in-open-state` (10s)

### Step 4: Manual Resolution
If sagas need manual cleanup:
1. Connect to PostgreSQL: `docker exec -it upi-postgres psql -U upi_admin -d upi_transactions`
2. Find stuck transactions:
   ```sql
   SELECT id, payment_id, status, created_at
   FROM transactions
   WHERE status = 'PENDING'
     AND created_at < NOW() - INTERVAL '10 minutes'
   ORDER BY created_at ASC;
   ```
3. Decision: Mark as FAILED (with compensation) or retry

---

## Runbook 4: Outbox Backlog (SEV2)

**Trigger**: Alarm `revpay-outbox-backlog` — OutboxPendingCount > 100 for 10 min

### Step 1: Check Outbox Table
1. `docker exec -it upi-postgres psql -U upi_admin -d upi_users`
2. ```sql
   SELECT COUNT(*) as pending, MIN(created_at) as oldest
   FROM outbox_events
   WHERE published = false;
   ```
3. Is the oldest entry > 10 minutes? → Publisher is stuck or not running

### Step 2: Check Outbox Publisher
1. Check transaction-service / user-service logs for outbox-related errors
2. Log Insights query:
   ```
   fields @timestamp, message, level
   | filter message like /outbox/i
   | sort @timestamp desc
   | limit 20
   ```

### Step 3: Check Kafka Connectivity
1. `docker exec -it upi-kafka kafka-broker-api-versions --bootstrap-server localhost:9092`
2. If Kafka is unreachable → `docker restart upi-kafka`
3. Check Zookeeper: `docker logs upi-zookeeper --tail 20`

### Resolution
| Root Cause | Action |
|---|---|
| Kafka broker down | `docker restart upi-kafka` |
| Outbox scheduler crashed | Restart the service |
| DB connection exhausted | Check HikariCP pool, restart service |

---

## Runbook 5: ALB Unhealthy Targets (SEV1)

**Trigger**: Alarm `revpay-alb-unhealthy` — UnHealthyHostCount > 0 for 2 min

### Step 1: Identify Which Target
1. Open **AWS Console → EC2 → Target Groups → revpay-api-gateway-tg**
2. Check the **Targets** tab → which instance is unhealthy?
3. Note the **health check reason** (e.g., "Request timeout", "Connection refused")

### Step 2: Check EC2 Instance
1. SSH into the unhealthy EC2 instance
2. `docker ps` → Is the API Gateway container running?
3. `docker logs upi-api-gateway --tail 30` → Check for errors
4. `curl http://localhost:8080/actuator/health` → Does it respond?

### Step 3: Investigate
| Health Check Failure | Likely Cause |
|---|---|
| Connection refused | Container not running or port not mapped |
| Request timeout | Application frozen (GC? deadlock?) |
| HTTP 503 | Application starting up or dependency unavailable |
| HTTP 500 | Application error in health check (check Redis connectivity) |

### Step 4: Resolution
1. If container stopped: `docker start upi-api-gateway`
2. If container crashed: `docker logs upi-api-gateway` → fix and redeploy
3. If instance unreachable: Check EC2 instance status in AWS Console
4. ALB will automatically re-register the target after 3 successful health checks (90s)

---

## Runbook 6: Billing Alert (MOST IMPORTANT)

**Trigger**: Alarm `revpay-billing-alert` — EstimatedCharges > $1.00

### Step 1: Find What's Charging
1. Open **AWS Console → Billing → Bills**
2. Check **Cost Explorer** → Group by Service → last 7 days
3. Identify the service charging you

### Step 2: Common Free Tier Overages
| Service | Likely Cause | Fix |
|---|---|---|
| CloudWatch | Too many custom metrics (> 10) | Remove extra metrics |
| CloudWatch | Log ingestion > 5 GB | Reduce log levels to WARN |
| CloudWatch | Too many dashboards (> 3) | Delete extra dashboards |
| EC2 | Instance running > 750 hours or wrong type | Stop instance, use t2.micro only |
| ALB | Over 750 hours or > 15 LCUs | Stop ALB when not testing |
| Data Transfer | Outbound data > 1 GB | Reduce test traffic volume |
| X-Ray | > 100k traces recorded | Lower sampling rate |

### Step 3: Immediate Cost Mitigation
1. **Stop the ALB** if not actively testing (biggest cost risk)
2. **Stop EC2 instances** when not using them
3. **Delete extra CloudWatch dashboards** if > 3
4. **Lower X-Ray sampling** to 0.01 (1%)
5. **Set log levels to ERROR** temporarily to reduce log volume

---

## Quick Reference: Alarm → Runbook Map

| Alarm | Runbook | Severity |
|---|---|---|
| `revpay-payment-failures-critical` | Runbook 1 | SEV1 |
| `revpay-5xx-errors-critical` | Runbook 1 | SEV1 |
| `revpay-latency-high` | Runbook 2 | SEV2 |
| `revpay-saga-stuck` | Runbook 3 | SEV2 |
| `revpay-outbox-backlog` | Runbook 4 | SEV2 |
| `revpay-rate-limit-surge` | Check gateway logs | SEV2 |
| `revpay-alb-unhealthy` | Runbook 5 | SEV1 |
| `revpay-alb-5xx` | Runbook 1 + 5 | SEV1 |
| `revpay-idempotency-surge` | Check for retry storms | SEV3 |
| `revpay-billing-alert` | Runbook 6 | SEV1 |
