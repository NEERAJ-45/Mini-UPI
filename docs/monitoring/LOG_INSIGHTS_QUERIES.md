# 🔍 CloudWatch Log Insights — Query Library

Pre-built queries for investigating issues in the RevPay payment system.
Run these in the **CloudWatch Console → Logs → Log Insights**.

> Select the appropriate log group(s) before running a query.
> Multiple log groups can be selected to search across services.

---

## 🔥 Incident Response Queries

### Find all errors in the last hour
```
fields @timestamp, traceId, service, message, level
| filter level = "ERROR"
| sort @timestamp desc
| limit 100
```

### Find all errors for a specific service
```
fields @timestamp, traceId, message, @logStream
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```
> Run against: `/revpay/dev/{service-name}`

### Track a single request across ALL services
```
fields @timestamp, service, message, sagaState, level
| filter traceId = "PASTE-YOUR-TRACE-ID-HERE"
| sort @timestamp asc
```
> Run against: ALL log groups (select all 5 service log groups)

---

## 💳 Transaction & Payment Queries

### Recent payment failures
```
fields @timestamp, traceId, userId, message, metadata.amount, metadata.errorCode
| filter service = "transaction-service"
  and level = "ERROR"
  and message like /payment|transaction|transfer/i
| sort @timestamp desc
| limit 50
```

### Saga state transitions for a specific transaction
```
fields @timestamp, sagaState, message, metadata.paymentId
| filter traceId = "PASTE-TRACE-ID"
  and service = "transaction-service"
| sort @timestamp asc
```

### Stuck sagas (PENDING for too long)
```
fields @timestamp, traceId, metadata.paymentId, message
| filter sagaState = "PENDING"
  and service = "transaction-service"
| sort @timestamp desc
| limit 50
```
> Cross-reference timestamps — if a saga has been PENDING for > 5 minutes, it's stuck.

### Daily payment volume
```
stats count(*) as total by bin(1h) as hour
| filter message like /payment.*success/i
  and service = "transaction-service"
| sort hour asc
```

### Payment latency distribution
```
stats avg(@duration) as avg_ms,
      pct(@duration, 50) as p50_ms,
      pct(@duration, 95) as p95_ms,
      pct(@duration, 99) as p99_ms
  by bin(5m) as time_window
| filter path like /transactions/
| sort time_window desc
```

---

## 📤 Outbox & Event Queries

### Outbox publishing failures
```
fields @timestamp, traceId, message, level
| filter service = "user-service"
  and message like /outbox/i
  and level = "ERROR"
| sort @timestamp desc
| limit 30
```

### Outbox event publishing rate
```
stats count(*) as events_published by bin(5m) as window
| filter message like /outbox.*publish/i
| sort window desc
```

### Kafka consumer lag warnings
```
fields @timestamp, service, message, metadata.consumerGroup, metadata.lag
| filter message like /consumer.*lag/i
  or message like /offset.*behind/i
| sort @timestamp desc
| limit 50
```

---

## 🔐 Authentication & User Queries

### Failed login attempts
```
fields @timestamp, userId, message, path, metadata.reason
| filter service = "user-service"
  and level = "WARN"
  and message like /login.*fail|auth.*fail|invalid.*credential/i
| sort @timestamp desc
| limit 50
```

### New user registrations
```
fields @timestamp, userId, message
| filter service = "user-service"
  and message like /register|user.*created/i
  and level = "INFO"
| sort @timestamp desc
| limit 50
```

---

## 🛡️ Gateway & Rate Limiting Queries

### Rate-limited requests
```
fields @timestamp, path, message, metadata.clientIp
| filter service = "api-gateway"
  and message like /rate.*limit|throttl|429/i
| sort @timestamp desc
| limit 50
```

### Request volume by endpoint
```
stats count(*) as request_count by path
| filter service = "api-gateway"
| sort request_count desc
| limit 20
```

### Slow requests (> 1 second)
```
fields @timestamp, path, @duration, traceId, service
| filter @duration > 1000
| sort @duration desc
| limit 30
```

---

## 🔧 Infrastructure & Debug Queries

### 5xx errors by service (last 24h)
```
stats count(*) as error_count by service
| filter level = "ERROR"
  and (message like /5[0-9][0-9]/ or message like /internal.*error/i)
| sort error_count desc
```

### Exception stack traces
```
fields @timestamp, service, message, @message
| filter @message like /Exception|StackTrace|at com\.neeraj/
| sort @timestamp desc
| limit 20
```

### Log volume by service (are we burning free tier?)
```
stats sum(@bytes) / 1048576 as MB_ingested by service
| sort MB_ingested desc
```
> Use this to check if you're approaching the 5 GB free tier limit.

---

## 💡 Tips

1. **Select multiple log groups** to search across services simultaneously
2. **Time range matters** — use narrow ranges to avoid scanning too much data
3. **Save frequently used queries** in CloudWatch console (Queries → Save)
4. **Cost**: Log Insights charges $0.005 per GB scanned — but free tier gives 5 GB/month of querying free
5. **Trace correlation**: Copy a `traceId` from logs → paste into X-Ray search → see full visual timeline
