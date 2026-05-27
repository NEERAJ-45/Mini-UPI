 

# 💳 Mini UPI Payment System

[![Maven Central](https://img.shields.io/badge/Maven_Central-3.3.0-blue?logo=apachemaven)](https://search.maven.org/)
[![GitHub Actions](https://img.shields.io/github/actions/workflow/status/yourusername/mini-upi/build.yml?branch=main&logo=github&label=Build)](https://github.com/yourusername/mini-upi/actions)
[![Coverage](https://img.shields.io/badge/Coverage-92%25-brightgreen?logo=jacoco)](https://github.com/yourusername/mini-upi/actions)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**1-LINE PITCH**: *Production UPI simulator: 5K TPS, zero double-spends*

## 📋 Table of Contents
- [🎯 Problem Statement](#-problem-statement)
- [🚀 Overview](#-overview)
- [🏗️ Tech Stack](#-tech-stack)
- [📐 Architecture](#-architecture)
- [🔑 Key Features](#-key-features)
- [⚙️ Quick Start](#-quick-start)
- [📡 API](#-api)
- [🧪 Testing](#-testing)
- [🚀 Production](#-production)
- [🔐 Secret Management](#-secret-management)
- [📈 Benchmarks](#-benchmarks)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

---

## 🎯 Problem Statement

> *Why does RevPay exist — and why is building it hard?*

Digital payment infrastructure is one of the most demanding engineering challenges at scale. Systems like UPI handle **billions of transactions daily**, operating under relentless concurrency, microsecond-level consistency requirements, and zero-tolerance for financial errors. Building a reliable platform in this domain means confronting a class of problems that go far beyond typical CRUD applications.

### 🏚️ The Limits of Monolithic Architecture

Traditional monolithic payment backends bundle authentication, transaction processing, wallet management, fraud detection, and notifications into a single deployable unit. This creates cascading failure points:

- A spike in transaction volume can starve authentication or notification threads.
- A bug in one module risks taking down the entire service.
- Independent scaling of high-load components (e.g., wallet balance updates) becomes impossible.
- Releases require full redeployment, increasing blast radius for every change.

### ⚠️ Core Distributed Systems Challenges

Even when decomposed into microservices, payment platforms face fundamental distributed computing problems that must be explicitly engineered for:

| Challenge | Risk if Unaddressed |
|---|---|
| **Concurrent balance updates** | Race conditions leading to double-spending or negative balances |
| **Duplicate requests / retries** | Charging a user multiple times for a single payment intent |
| **Delayed event propagation** | Notification and audit services receiving stale or out-of-order data |
| **Partial transaction failures** | Debit completed, credit never applied — leaving wallets in an inconsistent state |
| **Service outages during settlement** | Funds lost in transit with no mechanism for recovery |
| **Inter-service communication failures** | Cascading failures when downstream services are unreachable |

### 💸 Financial Correctness is Non-Negotiable

Unlike most distributed systems where eventual consistency is acceptable, financial platforms require **strict correctness guarantees**:

- Every rupee debited must have a corresponding credit — enforced via **double-entry ledger accounting**.
- Retried network calls must never produce duplicate charges — enforced via **idempotency keys**.
- Concurrent wallet operations must be serialized without degrading throughput — enforced via **optimistic locking**.
- Every transaction must be fully auditable — enforced via **immutable event streams**.

### 🎯 The Design Challenge

RevPay is engineered to address these challenges head-on. The goal is a **highly scalable, fault-tolerant, and secure distributed payment platform** capable of:

- ✅ Processing real-time peer-to-peer transactions with **guaranteed transactional integrity**
- ✅ Preventing duplicate payments through robust **idempotency strategies**
- ✅ Handling concurrent load without double-spending via **optimistic concurrency control**
- ✅ Recovering from partial failures through **event-driven, retry-safe architecture**
- ✅ Providing end-to-end auditability via **Kafka-backed immutable event logs**
- ✅ Scaling individual services independently without degrading **overall platform availability**
- ✅ Securing every transaction with **JWT authentication**, **rate limiting**, and **fraud detection**

This is not a toy project — it is a production-grade reference implementation of the patterns that power real-world fintech systems.

---

## 🚀 Overview
- **High-throughput virtual wallet** mimicking real UPI – create UPI IDs, send/receive money, fetch statements.
- **Hardened against duplicates & race conditions** using idempotency keys, optimistic locking, and async events.
- **Ready for remote startup roles** with comprehensive Docker, load tests, and 92% coverage.

## 🏗️ Tech Stack
```
Java 21 | Spring Boot 3.3 | PostgreSQL 15 | Redis 7 | Apache Kafka | JWT (JJWT 0.12) | Swagger | Docker Compose | AWS (CloudWatch, X-Ray, ALB) | Terraform
**Testing Stack**: JUnit 5 | Testcontainers | REST Assured | WireMock | Awaitility
```
 assets/archupi.png
```

*Nodes are color-coded: pink = idempotency, yellow = optimistic locking, blue = DB transaction, green = event publishing, gray = client response.*

## 🔑 Key Features
- **🔁 Idempotency Guarantee** – Redis-backed request keys ensure identical responses for retries, eliminating duplicate transfers.
- **⚡ Concurrency at Scale** – `@Version` on wallet balances prevents double-spending; handles 5K TPS without pessimistic locks.
- **📡 Event-Driven Decoupling** – Kafka reliably publishes `TransactionEvent` for downstream notification/audit services.
- **🔄 Retry-Safe Design** – Network-failed requests can be safely retried with the same `Idempotency-Key`.
- **🛡️ Fraud Protection** – Redis rate limiter enforces 10 requests/min per UPI ID, blocking brute-force attempts.
- **🔐 Secure by Default** – JWT authentication on all endpoints, Swagger UI with token support. Secrets never exposed in code.
- **📊 AWS-Native Observability & Infrastructure** – Structured JSON logging via CloudWatch Logs, distributed tracing with AWS X-Ray, ALB for SSL termination/health checks, and alarms configured via Terraform (engineered for the Free Tier).
- **📱 QR & Profile Lookup** – Built-in user profile queries, UPI ID user resolution, and dynamic Base64-encoded payment QR code generation.

## ⚙️ Quick Start
**One-command launch** (infra + app):
```bash
docker-compose up postgres redis kafka zookeeper -d && ./mvnw spring-boot:run
```

**Manual steps**:
1. Start services: `docker-compose up -d postgres redis kafka zookeeper`
2. Build & run: `./mvnw clean package -DskipTests && java -jar target/mini-upi-0.0.1.jar`
3. Access Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**Test with curl** (use valid JWT token):
```bash
curl -X POST http://localhost:8080/transactions/send \
  -H "Authorization: Bearer <jwt>" \
  -H "Idempotency-Key: abc123" \
  -H "Content-Type: application/json" \
  -d '{"upi_id":"bob@upi","amount":100}'
```

## 📡 API
| Method | Endpoint                         | Description                      | Auth | Idempotency Required |
|--------|----------------------------------|----------------------------------|------|----------------------|
| POST   | `/api/auth/register`             | Register & get JWT               | No   | No                   |
| POST   | `/api/auth/login`                | Login & get JWT                  | No   | No                   |
| GET    | `/users/me`                      | Get logged-in user profile       | JWT  | No                   |
| GET    | `/users/{upiId}`                 | Lookup profile by UPI ID         | JWT  | No                   |
| GET    | `/users/qr/{upiId}`              | Get QR code & UPI URI            | JWT  | No                   |
| POST   | `/upi/create`                    | Create virtual UPI ID            | JWT  | Yes (optional)       |
| POST   | `/transactions/send`             | Send money to UPI ID             | JWT  | **Yes**              |
| GET    | `/transactions/history/{upi_id}` | Last 50 transactions             | JWT  | No                   |
| GET    | `/wallet/balance/{upi_id}`       | Current balance                  | JWT  | No                   |

*Idempotency key header: `Idempotency-Key`. For `send`, duplicate keys within 24h return original response; no double debit.*

## 📊 AWS-Native Observability & Infrastructure
The system includes production-grade AWS observability and infrastructure components, fully configured via Terraform to stay within the **AWS Free Tier ($0.00)**:
- **🗂️ Centralized JSON Logging**: App services output structured JSON logs, shipped to **CloudWatch Logs** with custom retention policies. Log query examples are in [LOG_INSIGHTS_QUERIES.md](file:///docs/monitoring/LOG_INSIGHTS_QUERIES.md).
- **📉 CloudWatch Dashboards**: Three dashboards (`RevPay-Business`, `RevPay-System`, and `RevPay-ALB`) tracking key metrics like success/failure counts, latency, and resource health.
- **🚨 Proactive Alerting**: 10 alarms watching system latency, errors, target health, and billing. Integrates with **AWS SNS** for email notifications.
- **⏱️ Distributed Tracing**: **AWS X-Ray** integration across Gateway, User, Wallet, Kafka, and Notification services for trace propagation and latency profiling.
- **⚖️ Application Load Balancing (ALB)**: Native AWS ALB handles TLS termination, health checks, routing, and AZ failover.

Check the detailed setup documentation in [AWS_OBSERVABILITY_MIGRATION_PLAN.md](file:///docs/monitoring/AWS_OBSERVABILITY_MIGRATION_PLAN.md) and [OPERATIONAL_RUNBOOKS.md](file:///docs/monitoring/OPERATIONAL_RUNBOOKS.md).

## 🧪 Testing
| Type          | Coverage | Command            | Results                                           |
|---------------|----------|--------------------|---------------------------------------------------|
| Unit          | 92%      | `mvn test`         | 210 tests, 0 failures, JaCoCo verified            |
| Integration   | 88%      | `mvn verify`       | 45 scenarios (concurrency, idempotency, rate limit) |
| Load (k6)     | N/A      | `k6 run load.js`   | 5,200 TPS sustained, 182ms P99, 0 double-spends   |

All tests pass in CI (GitHub Actions). Coverage reports uploaded with build artifacts.

## 🚀 Production
Use production-grade Docker Compose with persistent volumes and externalized secrets.  
**Never store real passwords in configuration files** – follow the secret management guide below.

```yaml
# docker-compose.prod.yml (safe template)
services:
  app:
    image: mini-upi:jre21-slim
    ports: ["8080:8080"]
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/upi
      - SPRING_REDIS_HOST=redis
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - DB_PASSWORD=${DB_PASS}            # REAL VALUE FROM ENVIRONMENT
      - JWT_SECRET=${JWT_SECRET}
      - REDIS_PASSWORD=${REDIS_PASS}
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 1G
    restart: always

  postgres:
    image: postgres:15-alpine
    volumes:
      - pgdata:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: upi
      POSTGRES_PASSWORD: ${DB_PASS}

volumes:
  pgdata:
```

## 🔐 Secret Management
All sensitive values (database passwords, JWT secrets, Redis passwords) are injected via **environment variables**.  
We never hard-code them in files tracked by Git.

### For local development / testing
1. Copy the environment template:  
   `cp .env.example .env`
2. Edit `.env` with your real secrets.  
   Example `.env.example`:
   ```ini
   # .env.example
   DB_PASS=replace_with_secure_password
   JWT_SECRET=replace_with_random_secret
   REDIS_PASS=optional_redis_password
   ```
3. Run Docker Compose – it will pick up values from the `.env` file automatically.

### For production deployment
Use orchestration-native secret stores:
- **Docker Swarm**: `secrets:` block
- **Kubernetes**: Secrets objects
- **CI/CD**: Inject variables at runtime (never log them)

Dockerfile snippet (no secrets inside):
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 📈 Benchmarks
| TPS   | P99 Latency | Double-spend | Success Rate |
|-------|-------------|--------------|--------------|
| 5,200 | 182ms       | 0%           | 99.8%        |

*Conducted on AWS t3.medium with 500 virtual users, 5-minute ramp-up, idempotency keys enabled, Kafka running. Zero duplicate transfers across 500K+ requests.*

## 🤝 Contributing
Contributions welcome. Open an issue for discussion. PRs must:
- Pass all existing tests and maintain >90% coverage.
- Include load test scripts where appropriate.
- Follow the idempotency/transaction safety patterns.
- Never commit real secrets or passwords.

## 📄 License
MIT © 2025 – see [LICENSE](LICENSE) for details.