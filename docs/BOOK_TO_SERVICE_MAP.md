# 📚 Book → Microservice Learning Map (Mini-UPI)

> **How to use this:** Before coding a service, read the marked chapters from the books listed under it.
> Each entry shows **what to read** and **exactly why it applies** to that service.

---

## 🔵 Service 1: User Service (Port 8081)
*JWT Auth, BCrypt, UPI ID Gen, Outbox Pattern, Kafka Producer*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Start Here** | Ch. 2-5: IoC, DI, Beans, AOP | Core Spring setup for `UserService`, `JwtService`, `SecurityConfig` |
| **Spring Boot in Action** | Ch. 1 (Auto-config), Ch. 4 (Security), Ch. 5 (Profiles) | `SecurityConfig`, BCrypt, `application-dev.yml` profiles, JWT filter chain |
| **Effective Java** | Items 1, 15-17 (Immutability), 69-72 (Exceptions) | `UserRegistrationRequest` DTOs should be immutable; proper exception handling in `register()` |
| **Java Concurrency in Practice** | Ch. 2-3: Thread Safety & Sharing | Relevant when `OutboxEvent` scheduler runs concurrently with registration requests |
| **Clean Code** | Ch. 2 (Naming), Ch. 3 (Functions), Ch. 7 (Error Handling) | Keep `UserService`, `JwtService`, and `UpiIdGenerator` clean and single-responsibility |
| **Designing Data-Intensive Applications** | Ch. 7: Transactions | Understand WHY you need the Outbox Pattern: the dual-write problem between DB save and Kafka publish |
| **Patterns of Enterprise Application Architecture** | Unit of Work, Repository, Service Layer patterns | Architect `UserRepository`, `UserService` layering cleanly |
| **Domain-Driven Design** | Ch. 1-3: Entities, Value Objects, Aggregates | `User` is an Aggregate Root; `UpiId` is a Value Object |

---

## 🟢 Service 2: Wallet Service (Port 8082)
*Optimistic Locking, Ledger Double-Entry, Kafka Consumer, Transactional Mechanics*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Start Here** | Ch. 12-13: Spring Data JPA & Transactions | `@Transactional` on `transfer()`, `@Version` optimistic locking, JPA queries |
| **Effective Java** | Items 48-51 (Streams), 78-84 (Concurrency) | Safe concurrent balance reads/writes; item 78 specifically on synchronizing shared mutable data |
| **Java Concurrency in Practice** | Ch. 10-12: Avoiding Liveness Hazards, Performance | How `@Version` Optimistic Locking avoids deadlocks vs Pessimistic Locking |
| **Designing Data-Intensive Applications** | Ch. 7 (Transactions, ACID, Isolation Levels), Ch. 8 (Trouble with Distributed Systems) | Core theory behind `@Transactional` transfer, what happens when concurrent writes hit the same wallet row |
| **Database Internals** | Ch. 5: Transaction Processing, MVCC | Deep-dive into HOW PostgreSQL handles concurrent writes at the engine level (MVCC) — explains why `@Version` works |
| **Kafka: The Definitive Guide** | Ch. 4: Consumers, Ch. 6: Reliable Delivery | `UserCreatedListener` consumer group setup, at-least-once delivery guarantees, idempotent consumption |
| **Patterns of Enterprise App. Architecture** | Ch. 5: Concurrency (Optimistic Offline Lock) | The exact pattern behind `@Version` on the `Wallet` entity |
| **Microservices Patterns** | Ch. 4: Managing Transactions with Sagas | How Wallet Service participates as a Saga participant in a distributed transaction |

---

## 🔴 Service 3: Transaction Service (Port 8083)
*Saga Orchestration, Redis Idempotency, Fraud Engine, Feign Client, State Machine*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Boot in Action** | Ch. 6: Actuator, Ch. 7: Testing | Instrument `PaymentOrchestrator` with metrics; write integration tests for the saga flow |
| **Java Concurrency in Practice** | Ch. 12-13: Testing & Performance | Test `IdempotencyService` under concurrent duplicate-request scenarios |
| **Designing Data-Intensive Applications** | Ch. 7 (Transactions), Ch. 9 (Consistency & Consensus) | Redis idempotency key design; why `setIfAbsent` is the right atomic primitive; distributed locks theory |
| **Building Microservices** | Ch. 4 (Integration), Ch. 12 (Resiliency) | Feign client design, circuit breaker patterns, fallback strategies when Wallet Service is down |
| **Kafka: The Definitive Guide** | Ch. 3: Producers, Ch. 9: Exactly-Once | Publishing `txn.completed` and `txn.failed` reliably; producer idempotence settings |
| **Microservices Patterns** | Ch. 4 (Sagas), Ch. 7 (CQRS), Ch. 11 (Production Readiness) | **Core chapter** — the entire `PaymentOrchestrator` saga flow maps directly here |
| **System Design Interview** | Ch. 6: Design a Payment System | End-to-end design validation — compare your architecture against a reference design |
| **Domain-Driven Design** | Ch. 8: Domain Events | `txn.completed` / `txn.failed` are Domain Events; design their payload correctly |
| **Patterns of Enterprise App. Architecture** | State Machine Pattern | `Transaction` status: `PENDING → SUCCESS / FAILED` — implement as a clean state machine |

---

## 🟡 Service 4: Notification Service (Port 8084)
*Kafka Consumer, Async Processing, Alert Formatting*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Start Here** | Ch. 14: Spring Events & Async | `@KafkaListener` + `@Async` for non-blocking alert processing |
| **Kafka: The Definitive Guide** | Ch. 4: Consumers, Ch. 8: Cross-Cluster Replication | Consumer group config, partition rebalancing, lag monitoring for notification consumers |
| **Clean Code** | Ch. 3 (Functions), Ch. 9 (Unit Tests) | Keep `NotificationService` alert formatting methods clean; test each alert template |
| **Building Microservices** | Ch. 11: Microservices at Scale | Graceful consumer shutdown, consumer lag alerting — production concerns for this service |

---

## 🟠 Service 5: API Gateway (Port 8080)
*Reactive Programming, JWT Edge Validation, Rate Limiting, Routing*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Boot in Action** | Ch. 4: Security | JWT filter chain — adapted for reactive WebFlux pipeline in Gateway |
| **Spring Start Here** | Ch. 15: Reactive with Spring WebFlux | The Gateway runs on **Project Reactor** (Mono/Flux) — this chapter is mandatory |
| **Designing Data-Intensive Applications** | Ch. 12: The Future of Data Systems | Rate limiting theory — token bucket vs leaky bucket; Redis-backed implementation rationale |
| **System Design Interview** | Ch. 4: Rate Limiter Design | Direct mapping — use this to design the Redis token-bucket rate limiter in `GatewayConfig` |
| **Building Microservices** | Ch. 9: Security, Ch. 13: At Scale | JWT edge validation strategy, mutual TLS between services, API gateway as security boundary |
| **The Art of Scalability** | Ch. 9-11: Capacity Planning & Load Testing | Understanding how your Gateway handles 5K TPS — request queuing, connection pools |

---

## 🟣 Service 6: Monitoring & Observability Stack
*Prometheus, Grafana, Micrometer, SLIs/SLOs*

| Book | Chapters / Topics to Focus | Why It Applies |
|------|---------------------------|----------------|
| **Spring Boot in Action** | Ch. 6: Actuator | `management.endpoints.web.exposure.include=prometheus` — the exact config to expose metrics |
| **Site Reliability Engineering** | Ch. 4 (SLOs), Ch. 6 (Monitoring), Ch. 14 (Alerting) | Define SLIs (latency p99, error rate) and SLOs for your Grafana dashboards |
| **The DevOps Handbook** | Part III: Technical Practices of Feedback | Set up alerting pipelines, define runbooks for common failures (Kafka lag spike, OOM) |
| **Designing Data-Intensive Applications** | Ch. 1: Reliability, Scalability, Maintainability | Foundational theory — what metrics ACTUALLY matter for a payment system |
| **The Art of Scalability** | Ch. 12-14: Monitoring, Metrics, Performance | Practical guidance on choosing the right metric cardinality for Prometheus |
| **Accelerate** | Ch. 2: Measuring Performance | Use DORA metrics (deployment frequency, lead time, MTTR) to measure your dev pipeline |

---

## 📖 Master Priority Reading Order for This Project

Start here, in this exact sequence, as you build each service:

```
BEFORE User Service:
  1. Spring Start Here (Ch. 1-7)
  2. Spring Boot in Action (Ch. 1, 4, 5)
  3. Clean Code (Ch. 1-7)
  4. Effective Java (Items 1, 15-17, 69-72)

BEFORE Wallet Service:
  5. Java Concurrency in Practice (Ch. 1-5, 10-12)
  6. DDIA (Ch. 7 — Transactions) ← MOST IMPORTANT
  7. Database Internals (Ch. 5)
  8. Patterns of Enterprise App. Architecture (Ch. 5)

BEFORE Transaction Service:
  9. Microservices Patterns (Ch. 4 — Sagas) ← CRITICAL
  10. Kafka: The Definitive Guide (Ch. 3-4)
  11. DDIA (Ch. 9 — Consistency & Consensus)
  12. System Design Interview (Ch. 6 — Payment System)

BEFORE API Gateway:
  13. Spring Start Here (Ch. 15 — WebFlux)
  14. Building Microservices (Ch. 9, 13)
  15. System Design Interview (Ch. 4 — Rate Limiter)

BEFORE Monitoring:
  16. Spring Boot in Action (Ch. 6 — Actuator)
  17. Site Reliability Engineering (Ch. 4, 6, 14)
  18. The DevOps Handbook (Part III)
```

---

> **Tip:** You don't need to finish each book — read the relevant chapters, build that service, then move forward. Come back for deeper chapters as you encounter real problems.
