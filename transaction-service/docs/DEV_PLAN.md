# 🧠 Transaction Service — Detailed Development Plan
**Port:** `8083` | **DB:** PostgreSQL | **Kafka:** Producer (`txn.completed`, `txn.failed`) | **Cache:** Redis (Idempotency)

---

## 🎯 Service Objective
The **orchestration brain** of the system. Receives payment requests, enforces idempotency via Redis, runs fraud checks, calls Wallet Service via Feign for the actual transfer, and publishes outcome events to Kafka.

---

## 📦 Package Structure
```
com.neeraj.upi.transaction
├── controller/        TransactionController.java
├── service/
│   ├── PaymentOrchestrator.java
│   ├── IdempotencyService.java
│   └── FraudEngine.java
├── client/            WalletFeignClient.java
├── entity/            Transaction.java
├── repository/        TransactionRepository.java
├── dto/               PaymentRequest.java, PaymentResponse.java
├── exception/         FraudVelocityException.java, DuplicateRequestException.java
└── TransactionServiceApplication.java
```

---

## 🏗️ Phase 3.1 — Transaction Entity & State Machine

### `Transaction.java` (Entity)
- Fields: `UUID txnId`, `String senderUpi`, `String receiverUpi`, `BigDecimal amount`, `TransactionStatus status`, `String failureReason`, `String idempotencyKey`, `LocalDateTime createdAt`, `LocalDateTime updatedAt`
- `TransactionStatus` enum: `PENDING → SUCCESS | FAILED`
- State is **write-once forward** — a txn can only move PENDING→SUCCESS or PENDING→FAILED, never backwards

### `TransactionRepository.java`
- `Optional<Transaction> findByIdempotencyKey(String key)`
- `@Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t WHERE t.senderUpi = :upi AND t.status = 'SUCCESS' AND t.createdAt >= :startOfDay")`
  `BigDecimal sumSuccessfulAmountSince(@Param("upi") String upi, @Param("startOfDay") LocalDateTime start)`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Patterns of Enterprise App. Architecture** | State Machine pattern — model `Transaction` status transitions cleanly |
| **Domain-Driven Design** | Ch. 8: Domain Events — `txn.completed` / `txn.failed` are domain events |

---

## 🏗️ Phase 3.2 — Feign Client to Wallet Service

### `WalletFeignClient.java`
```java
@FeignClient(name = "wallet-service", url = "${wallet.service.url}")
public interface WalletFeignClient {
    @PostMapping("/internal/wallet/transfer")
    ApiResponse<?> transfer(@RequestBody TransferRequest request);
}
```

### Config (`application.yml`)
```yaml
wallet.service.url: http://localhost:8082
```

### Error Handling
- Feign throws `FeignException` on non-2xx responses
- `PaymentOrchestrator` catches this and marks txn as FAILED

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Building Microservices** | Ch. 4: Integration — sync vs async inter-service calls |
| **Building Microservices** | Ch. 12: Resiliency — circuit breakers, timeouts, retries |

---

## 🏗️ Phase 3.3 — Redis Idempotency

### `IdempotencyService.java`
- Uses `StringRedisTemplate`
- **`checkAndLock(String idempotencyKey)`**:
  1. `redisTemplate.opsForValue().setIfAbsent("idempotency:" + key, "PROCESSING", 24, TimeUnit.HOURS)`
  2. Returns `true` if new (lock acquired), `false` if duplicate
- **`markCompleted(String key, UUID txnId)`**: Updates Redis value to the `txnId` for future lookups
- **On duplicate request**: Look up existing txn from DB and return cached response (no new money moved)

### Why Redis?
DB-level idempotency (unique constraint on `idempotencyKey`) works but is slower. Redis `setIfAbsent` is atomic, sub-millisecond, and naturally expires after 24h.

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **DDIA** | Ch. 9: Consistency & Consensus — why `setIfAbsent` is the right atomic primitive |
| **System Design Interview** | Ch. 6: Design a Payment System — idempotency key design |

---

## 🏗️ Phase 3.4 — Fraud Engine

### `FraudEngine.java`
Three checks, executed in sequence:

**Check 1: Self-Transfer**
```java
if (senderUpi.equals(receiverUpi)) throw new FraudException("Self-transfer not allowed");
```

**Check 2: Per-Transaction Limit**
```java
BigDecimal maxPerTxn = new BigDecimal("10000"); // ₹10,000
if (amount.compareTo(maxPerTxn) > 0) throw new FraudException("Amount exceeds per-transaction limit");
```

**Check 3: Daily Velocity Limit**
```java
BigDecimal dailyLimit = new BigDecimal("100000"); // ₹1,00,000
BigDecimal todayTotal = transactionRepository.sumSuccessfulAmountSince(senderUpi, startOfToday());
if (todayTotal.add(amount).compareTo(dailyLimit) > 0) throw new FraudVelocityException("Daily limit exceeded");
```

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **System Design Interview** | Ch. 6: Payment System — fraud detection patterns |
| **DDIA** | Ch. 7: Transactions — race conditions in aggregate queries |

---

## 🏗️ Phase 3.5 — Saga Orchestration (The Core)

### `PaymentOrchestrator.pay(PaymentRequest request)`

**The 6-Step Flow:**
```
Step 1: IDEMPOTENCY CHECK
  └─ IdempotencyService.checkAndLock(request.idempotencyKey)
  └─ If duplicate → return cached response immediately

Step 2: SAVE PENDING TXN
  └─ Create Transaction(status=PENDING), save to DB

Step 3: FRAUD CHECKS
  └─ FraudEngine.validate(sender, receiver, amount)
  └─ If fraud detected → mark FAILED, publish txn.failed, return

Step 4: CALL WALLET SERVICE
  └─ WalletFeignClient.transfer(sender, receiver, amount, txnId)

Step 5: UPDATE STATUS
  └─ If Feign success → status = SUCCESS
  └─ If FeignException → status = FAILED, record failureReason

Step 6: PUBLISH EVENT
  └─ KafkaTemplate.send("txn.completed" or "txn.failed", event)
  └─ IdempotencyService.markCompleted(key, txnId)
```

### Exception Handling
- Wrap Step 4 in `try/catch(FeignException)`
- On ANY exception: update txn to FAILED, save reason, publish `txn.failed`

### Controller
- `POST /transactions/pay` — body: `PaymentRequest { senderUpi, receiverUpi, amount }`, header: `Idempotency-Key`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Microservices Patterns** | Ch. 4: Sagas — **the most important chapter for this service** |
| **Kafka: The Definitive Guide** | Ch. 3: Producers — reliable event publishing |
| **DDIA** | Ch. 9: Consistency — what happens if Kafka publish fails after DB commit |

---

## ✅ Testing Checklist
- [ ] Create two users, give User A ₹5000
- [ ] `POST /transactions/pay` ₹100 from A→B → SUCCESS, both balances correct
- [ ] Same request again (same Idempotency-Key) → returns cached txnId, no money moved
- [ ] Try ₹15,000 single txn → `FraudException` per-txn limit
- [ ] Try self-transfer → `FraudException`
- [ ] Exhaust daily limit → `FraudVelocityException`
- [ ] Check Kafka topics: `txn.completed` / `txn.failed` events published

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| Redis idempotency over DB unique constraint | Sub-ms atomic check; auto-expires after 24h |
| Saga Orchestrator (not Choreography) | Central control over payment flow — easier debugging and state tracking |
| Fraud checks BEFORE wallet call | Fail fast — don't hit Wallet Service unnecessarily |
| Idempotency-Key in HTTP header | Industry standard (Stripe, Razorpay) — separates business payload from infra concern |
