# Mini-UPI Project TODOs & Learning Tracker

This document tracks both the implementation phases and the required learning topics for building the distributed payment system.

## 📚 Learning TODOs (Just-In-Time)

### Phase 1 Topics
- [ ] Understand Asymmetric Cryptography (RSA/ECDSA) for JWT signing and verification.
- [ ] Study the **Transactional Outbox Pattern** to solve the dual-write problem.

### Phase 2 & 3 Topics
- [ ] Understand Kafka Core Concepts: Topics, Partitions, Consumer Groups, Offsets.
- [ ] Learn how to implement **Idempotent Consumers** to handle duplicate Kafka events.
- [ ] Learn about Redis Idempotency Keys (preventing duplicate API requests).

### Phase 4 Topics
- [ ] Study Distributed Transactions and the **Saga Pattern** (Choreography vs Orchestration).
- [ ] Understand **Eventual Consistency** and how to manage compensation (rollbacks) across services.

### Phase 5 & 6 Topics
- [ ] Read up on Circuit Breakers (Resilience4j).
- [ ] Understand Observability: Distributed Tracing (Jaeger) and Correlation IDs.

---

## 🛠️ Implementation Phases TODOs

### Phase 1: Authentication, Edge Layer & Outbox Pattern
- [ ] Complete `JwtService` in User Service using Asymmetric RSA Keys.
- [ ] Implement the **Transactional Outbox Pattern** in `UserService.register()`: save `UserCreatedEvent` to `outbox_events` table within the same DB transaction.
- [ ] Create a scheduled job or CDC connector to read from the outbox table and publish to Kafka ensuring at-least-once delivery.
- [ ] Implement API Gateway JWT validation filter (verifying via public key).
- [ ] Implement Redis-based rate limiting (IP/User level) in API Gateway.
- [ ] Add missing unit/integration tests for the authentication flow.

### Phase 2: Wallet Service & Async Onboarding
- [ ] Implement Kafka consumer in Wallet Service to listen for `UserCreatedEvent`.
- [ ] Create wallet entries in the database for newly registered users with a starting balance.
- [ ] Develop core Wallet APIs (Fetch Balance, Add Funds).

### Phase 3: Transaction Service & Idempotency
- [ ] Create Payment Initiation API (`/api/v1/payments/initiate`) in Transaction Service.
- [ ] Implement Redis-based Idempotency Filter (duplicate request prevention).
- [ ] Publish `payment.initiated` Kafka event.
- [ ] Scaffold Saga Orchestrator to track transaction state (PENDING, SUCCESS, FAILED).

### Phase 4: Core Payment Flow & Ledger (Double-Entry)
- [ ] Wallet Service: Consume `payment.initiated` event, perform balance validation, and reserve/hold funds.
- [ ] Ledger Service: Create double-entry records (debit sender, credit receiver).
- [ ] Ledger Service: Publish ledger success/failure events back to the Transaction Saga Orchestrator.
- [ ] Transaction Service: Commit or Rollback based on Ledger outcomes.

### Phase 5: Ancillary Services & Reliability
- [ ] Fraud Detection Service: Consume payment stream to analyze velocity and apply simple risk scoring.
- [ ] Notification Service: Consume successful/failed payment events and simulate SMS/Email.
- [ ] Reliability: Implement Retry Topics and Dead Letter Queues (DLQ) for failed Kafka messages.

### Phase 6: Observability & Production Readiness
- [ ] Integrate Prometheus and Grafana dashboards for business and system metrics.
- [ ] Deploy Jaeger for distributed tracing across the saga flow.
- [ ] Setup ELK stack for centralized log aggregation.
- [ ] Finalize GitHub Actions pipelines for automated testing and builds.
