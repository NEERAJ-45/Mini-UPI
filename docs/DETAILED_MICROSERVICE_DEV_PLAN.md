# In-Depth Microservice Development Plan (Phase-Wise)

This document provides a highly detailed, step-by-step development plan for each microservice in the Mini-UPI project. It expands on the high-level roadmap to define the exact classes, components, configurations, and logic required at each stage of development.

---

## 1. User Service & Identity (Port 8081)

**Objective:** Handle user registration, authentication (JWT), profile management, and UPI ID generation. Acts as the source of truth for identities and publishes `user.created` events.

### Phase 1.1: Core Domain & Data Access
- **Entities (`com.neeraj.upi.user.entity`)**:
  - Create `User` entity (`id`, `fullName`, `phone`, `pinHash`, `upiId`, `createdAt`, `updatedAt`).
- **Repositories (`com.neeraj.upi.user.repository`)**:
  - Create `UserRepository` extending `JpaRepository`. Add `boolean existsByPhone(String phone)` and `Optional<User> findByPhone(String phone)`.
- **DTOs (`com.neeraj.upi.user.dto`)**:
  - `UserRegistrationRequest`, `UserLoginRequest`, `AuthResponse` (contains JWT and User info).

### Phase 1.2: Business Logic & Security
- **Security Utilities (`com.neeraj.upi.user.security`)**:
  - Implement `JwtService` using JJWT v0.12+. Use `Keys.hmacShaKeyFor()` for signing. Methods: `generateToken(User user)`, `extractUsername(String token)`, `isTokenValid(String token)`.
  - Configure `SecurityConfig` to disable CSRF, set STATELESS session, and allow `/auth/**`.
- **Services (`com.neeraj.upi.user.service`)**:
  - `UpiIdGenerator`: Logic to format UPI ID (e.g., `first_name + phone_last_4 + @miniupi`).
  - `UserService`: 
    - `register()`: Validate phone uniqueness, hash PIN (BCrypt), save user, generate UPI ID, create JWT.
    - `login()`: Verify phone and PIN, return JWT.

### Phase 1.3: Event-Driven Outbox & APIs
- **Outbox Pattern (`com.neeraj.upi.user.outbox`)**:
  - Create `OutboxEvent` entity to store events before publishing to Kafka, ensuring atomic DB writes.
  - Create a `@Scheduled` job to poll `OutboxEvent` table and publish to Kafka (`user.created` topic).
- **Controllers (`com.neeraj.upi.user.controller`)**:
  - `AuthController`: Map `POST /auth/register` and `POST /auth/login`.

---

## 2. Wallet Service & Ledgers (Port 8082)

**Objective:** Manage user balances, process debits/credits safely using Optimistic Locking, and maintain an immutable ledger.

### Phase 2.1: Domain & Event Consumption
- **Entities (`com.neeraj.upi.wallet.entity`)**:
  - `Wallet`: `id`, `userId`, `upiId`, `balance` (BigDecimal), `@Version Long version` (for Optimistic Locking).
  - `LedgerEntry`: `id`, `walletId`, `transactionId`, `amount`, `type` (CREDIT/DEBIT), `timestamp`.
- **Repositories (`com.neeraj.upi.wallet.repository`)**:
  - `WalletRepository`, `LedgerEntryRepository`.
- **Kafka Consumers (`com.neeraj.upi.wallet.listener`)**:
  - `UserCreatedListener`: Consumes `user.created`. Extracts `userId` and `upiId`, invokes `WalletService.createWallet()` idempotently.

### Phase 2.2: Core Transactional Mechanics
- **Services (`com.neeraj.upi.wallet.service`)**:
  - `WalletService`:
    - `createWallet()`: Initializes wallet with ₹0.00 balance.
    - `addMoney(String upiId, BigDecimal amount)`: Increments balance, creates `CREDIT` ledger entry.
    - `transfer(String senderUpi, String receiverUpi, BigDecimal amount)`: 
      - Wrap in `@Transactional`.
      - Fetch sender and receiver wallets.
      - Check `sender.balance >= amount`. Throw `InsufficientFundsException` if false.
      - Update balances. Rely on `@Version` to catch concurrent modifications (`OptimisticLockingFailureException`).
      - Create DEBIT for sender, CREDIT for receiver.

### Phase 2.3: APIs
- **Controllers (`com.neeraj.upi.wallet.controller`)**:
  - `WalletController`: `POST /wallet/add-money`, `GET /wallet/balance`. Note: `transfer` is typically called internally via Feign by the Transaction Service, but can have a secured internal endpoint.

---

## 3. Transaction Service & Orchestration (Port 8083)

**Objective:** Handle the Saga orchestration of payments, enforce idempotency, check for fraud (daily velocity limits), and manage transaction states.

### Phase 3.1: Transaction State & Feign Clients
- **Entities (`com.neeraj.upi.transaction.entity`)**:
  - `Transaction`: `txnId`, `senderUpi`, `receiverUpi`, `amount`, `status` (PENDING, SUCCESS, FAILED), `failureReason`, `timestamp`.
- **Feign Clients (`com.neeraj.upi.transaction.client`)**:
  - `WalletClient`: Interface to communicate with Wallet Service (`POST /internal/wallet/transfer`).

### Phase 3.2: Safety Checks (Idempotency & Fraud)
- **Idempotency (`com.neeraj.upi.transaction.service`)**:
  - `IdempotencyService`: Use `RedisTemplate`. On new request, try to set `idempotency:{requestId}` with `setIfAbsent()`. If false, block request or return cached result.
- **Fraud Controls (`com.neeraj.upi.transaction.service`)**:
  - `FraudEngine`: 
    - Check if `sender == receiver`.
    - Query DB: `SELECT SUM(amount) FROM transactions WHERE sender = ? AND date = TODAY`. Check against daily limit.

### Phase 3.3: Saga Orchestration & Event Publishing
- **Services (`com.neeraj.upi.transaction.service`)**:
  - `PaymentOrchestrator`:
    1. Validate Idempotency & Fraud.
    2. Save `Transaction` as PENDING.
    3. Try `WalletClient.transfer()`.
    4. If successful: Update to SUCCESS, publish `txn.completed` event to Kafka.
    5. If failed (e.g., FeignException): Update to FAILED, record reason, publish `txn.failed` event.

---

## 4. Notification Service (Port 8084)

**Objective:** Asynchronously consume events and send user alerts (simulated SMS/Emails).

### Phase 4.1: Event Consumption & Routing
- **Kafka Consumers (`com.neeraj.upi.notification.listener`)**:
  - `TransactionEventListener`: Listens to `txn.completed` and `txn.failed` topics.
- **Services (`com.neeraj.upi.notification.service`)**:
  - `NotificationService`: Evaluates event payload.
    - If SUCCESS: Format SMS mock logs for Sender (Debit Alert) and Receiver (Credit Alert).
    - If FAILED: Format SMS mock log for Sender (Failed Txn Alert).
  - Uses `SLF4J` Logger to simulate sending: `log.info("SMS Sent to {}: Your account is debited by {}...", user, amount);`

---

## 5. API Gateway (Port 8080)

**Objective:** Act as the single entry point, manage routing, perform edge-level JWT validation, and rate limit traffic.

### Phase 5.1: Routing & Rate Limiting
- **Configuration (`application.yml`)**:
  - Define routes for `/auth/**` (User Service), `/wallet/**` (Wallet Service), `/transactions/**` (Transaction Service).
- **Rate Limiter (`com.neeraj.upi.gateway.config`)**:
  - Configure Spring Cloud Gateway Redis RateLimiter.
  - Create `KeyResolver` bean to rate-limit by Client IP or User ID extracted from the token.

### Phase 5.2: Edge Security
- **Filters (`com.neeraj.upi.gateway.filter`)**:
  - `JwtAuthFilter`: Implements `GatewayFilter`.
  - intercepts incoming requests.
  - Checks path against a `PUBLIC_PATHS` list (e.g., `/auth/register`, `/auth/login`).
  - If protected, extracts `Authorization` header, validates JWT signature (locally using the same secret or via a shared library/JWKS), and forwards the request if valid, or throws `401 Unauthorized`.
