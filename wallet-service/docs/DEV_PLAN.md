# 💰 Wallet Service — Detailed Development Plan
**Port:** `8082` | **DB:** PostgreSQL | **Kafka:** Consumer (`user.created`) | **Cache:** None

---

## 🎯 Service Objective
Act as the **core ledger engine**. Auto-creates wallets on user registration (via Kafka), manages balance top-ups, and performs atomic double-entry bookkeeping on every transfer using Optimistic Locking to prevent double-spends.

---

## 📦 Package Structure
```
com.neeraj.upi.wallet
├── controller/        WalletController.java
├── service/           WalletService.java
├── listener/          UserCreatedListener.java
├── entity/            Wallet.java, LedgerEntry.java
├── repository/        WalletRepository.java, LedgerEntryRepository.java
├── dto/               AddMoneyRequest.java, TransferRequest.java, WalletResponse.java
├── exception/         InsufficientFundsException.java, WalletNotFoundException.java
└── WalletServiceApplication.java
```

---

## 🏗️ Phase 2.1 — Domain Entities & Optimistic Locking

### Entities
- **`Wallet.java`**: `UUID id`, `UUID userId`, `String upiId`, `BigDecimal balance`, `@Version Long version`, `LocalDateTime createdAt`
  - `@Column(precision=19, scale=4)` on balance
  - NEVER use `double` — monetary precision only with `BigDecimal`

- **`LedgerEntry.java`**: `UUID id`, `UUID walletId`, `UUID transactionId`, `BigDecimal amount`, `EntryType type` (CREDIT/DEBIT), `BigDecimal balanceAfter`, `LocalDateTime timestamp`
  - **Immutable audit log** — entries are appended, never modified

### Repositories
- `WalletRepository`: `findByUpiId(String upiId)`, `findByUserId(UUID userId)`
- `LedgerEntryRepository`: `findByWalletIdOrderByTimestampDesc(UUID walletId)`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **DDIA** | Ch. 7: Transactions, ACID, isolation levels |
| **Database Internals** | Ch. 5: MVCC — how PostgreSQL handles concurrent writes |
| **Patterns of Enterprise App. Architecture** | Optimistic Offline Lock pattern (Ch. 5) |
| **Effective Java** | Item 48: Use `BigDecimal` for monetary values |

---

## 🏗️ Phase 2.2 — Kafka Consumer: Auto Wallet Creation

### `UserCreatedListener.java`
- `@KafkaListener(topics = "user.created", groupId = "wallet-service-group")`
- Deserialize JSON → `UserCreatedEvent { userId, upiId }`
- Call `walletService.createWallet(userId, upiId)`
- **Must be idempotent** — check if wallet exists first; skip if duplicate event arrives

### `WalletService.createWallet()`
1. `walletRepository.findByUpiId(upiId).isPresent()` → if yes, return (idempotent)
2. Create `Wallet` with `balance = BigDecimal.ZERO`
3. Save

### Kafka Consumer Config (`application.yml`)
```yaml
spring.kafka.consumer:
  group-id: wallet-service-group
  auto-offset-reset: earliest
  value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
  properties.spring.json.trusted.packages: "com.neeraj.upi.*"
```

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Kafka: The Definitive Guide** | Ch. 4: Consumers, Consumer Groups, Offsets |
| **Kafka: The Definitive Guide** | Ch. 6: Reliable Delivery — at-least-once semantics |
| **Microservices Patterns** | Ch. 3: Transactional Messaging |

---

## 🏗️ Phase 2.3 — Add Money

### `WalletService.addMoney(String upiId, BigDecimal amount)`
1. Fetch wallet → throw `WalletNotFoundException`
2. Validate `amount > 0`
3. `wallet.setBalance(wallet.getBalance().add(amount))`
4. Save wallet (`@Version` auto-increments)
5. Save `LedgerEntry` CREDIT with `balanceAfter`

### Controller
- `POST /wallet/add-money` body: `{ upiId, amount }` → `200 OK`
- `GET /wallet/balance?upiId=` → returns balance

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Start Here** | Ch. 12: Spring Data JPA, `@Transactional`, entity lifecycle |

---

## 🏗️ Phase 2.4 — Transfer (The Critical Path)

### `WalletService.transfer(String senderUpi, String receiverUpi, BigDecimal amount, UUID txnId)`
Annotated: `@Transactional(isolation = Isolation.READ_COMMITTED)`

**Flow:**
1. Fetch sender wallet → `WalletNotFoundException`
2. Fetch receiver wallet → `WalletNotFoundException`
3. `sender.balance < amount` → throw `InsufficientFundsException`
4. `sender.balance.subtract(amount)` | `receiver.balance.add(amount)`
5. Save both wallets — JPA executes two UPDATEs with `WHERE version = ?`
6. Save `LedgerEntry` DEBIT for sender
7. Save `LedgerEntry` CREDIT for receiver

**On `OptimisticLockingFailureException`:**
- Propagates to Transaction Service via Feign → caught → txn marked `FAILED`

### Internal Endpoint
- `POST /internal/wallet/transfer` — called only by Transaction Service Feign client

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Java Concurrency in Practice** | Ch. 10: Avoiding deadlocks — why optimistic beats pessimistic |
| **DDIA** | Ch. 7: Write skew, phantom reads, why isolation level matters |
| **Patterns of Enterprise App. Architecture** | Unit of Work pattern |

---

## ✅ Testing Checklist
- [ ] Register user → verify `upi_wallets` row auto-created with `balance = 0.00`
- [ ] `POST /wallet/add-money` → balance updates, `ledger_entries` has CREDIT row
- [ ] Send same `user.created` Kafka event twice → only ONE wallet created
- [ ] Transfer more than balance → `InsufficientFundsException` returned
- [ ] Run concurrent add-money → no balance corruption

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| `@Version` Optimistic Locking | ~10x better throughput than `SELECT FOR UPDATE` at high TPS |
| `BigDecimal(19,4)` | Financial precision — float/double cause rounding errors |
| Immutable `LedgerEntry` | Append-only financial audit trail |
| Idempotent `createWallet()` | Kafka at-least-once means same event can arrive twice |
| Internal-only transfer endpoint | Transfer is privileged — not exposed via API Gateway |
