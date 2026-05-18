# System Design & Microservices Learning Path

To build a robust distributed payment system like Mini-UPI, there are several core system design and microservice concepts you should understand. Since you are dealing with financial transactions, the focus heavily shifts toward **consistency, reliability, and fault tolerance**.

## 1. Distributed Data Management (Crucial for Payments)
When you split a monolith into microservices, the hardest part is managing data across different databases.
*   **Database-per-Service Pattern:** Understand why services shouldn't share a database (isolation, coupling) and the challenges this introduces.
*   **CAP Theorem & PACELC:** Learn the trade-offs between Consistency, Availability, and Partition Tolerance. Payments usually favor Consistency over Availability.
*   **Distributed Transactions:** 
    *   Learn why **Two-Phase Commit (2PC)** is generally avoided in modern microservices (locking issues, performance).
    *   **The Saga Pattern:** Learn how to manage cross-service transactions using local transactions and compensating actions (rollbacks). Understand both *Choreography* (event-based) and *Orchestration* (central controller).
*   **The Dual-Write Problem:** Understand why writing to a database and publishing an event simultaneously is dangerous.
    *   **Transactional Outbox Pattern:** Learn how to guarantee message delivery using an outbox table.
    *   **Change Data Capture (CDC):** Learn how tools like Debezium read database transaction logs to publish events.

## 2. Event-Driven Architecture (Kafka)
Since your services will communicate asynchronously, understanding messaging is key.
*   **Eventual Consistency:** Get comfortable with the idea that the system might not be consistent at every exact millisecond, but will "eventually" converge to the correct state.
*   **Kafka Core Concepts:** Understand Topics, Partitions, Offsets, and Consumer Groups. Learn how partitions guarantee ordering.
*   **Idempotent Consumers:** Learn how to design your consumers (like your Wallet Service) so that if they receive the *same* Kafka event twice, they don't process it twice (e.g., deducting the balance twice).
*   **Failure Handling:** 
    *   **Retry Patterns:** Exponential backoff for transient failures (e.g., database temporarily down).
    *   **Dead Letter Queues (DLQ):** Where do messages go when they fail processing 5 times? How do you manually inspect and replay them?

## 3. Reliability & Resiliency
Networks fail, databases restart, and services crash. Your system must survive this.
*   **Idempotency Keys:** Learn how APIs use idempotency keys (usually UUIDs in headers) stored in Redis to prevent duplicate payment requests from users refreshing their browsers.
*   **Circuit Breaker Pattern:** Learn how to stop sending requests to a service that is currently failing, allowing it time to recover (e.g., using Resilience4j).
*   **Rate Limiting & Throttling:** Learn how to protect your APIs from abuse using Redis (Token Bucket or Leaky Bucket algorithms).

## 4. Security & Identity
Securing communication between the client and the edge, and between services.
*   **Stateless vs. Stateful Auth:** Understand why microservices prefer stateless JWTs over stateful session cookies.
*   **Asymmetric Cryptography for JWTs:** Learn how RSA Private/Public key pairs work. The Auth service uses the Private key to sign tokens; the API Gateway uses the Public key to verify them without needing to contact the Auth service.
*   **Zero Trust / Service-to-Service Auth:** Learn how services authenticate each other (mTLS or internal service tokens).

## 5. Observability (The "Three Pillars")
When a distributed transaction fails across 4 services, you need to know *exactly* where and why.
*   **Centralized Logging (ELK Stack):** Why you can't just SSH into servers to read logs anymore.
*   **Distributed Tracing (Jaeger / OpenTelemetry):** Learn how **Correlation IDs** (or Trace IDs) are injected at the API Gateway and passed in the headers to every downstream service so you can visualize the entire request path.
*   **Metrics (Prometheus & Grafana):** Tracking RED metrics (Rate, Errors, Duration) for your APIs.

## Recommended Approach
You don't need to master all of this before you start. It is recommended to learn them **just-in-time** as you hit each phase in the implementation plan:
1. Read up on **Asymmetric JWTs** before finishing the JWT Service.
2. Read up on the **Outbox Pattern** before you write the Kafka publisher for the User Service.
3. Read up on the **Saga Pattern** when we get to the Transaction/Ledger flow.
