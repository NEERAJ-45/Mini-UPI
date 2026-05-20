# 📊 Monitoring & Observability — Detailed Development Plan
**Components:** Spring Boot Actuator + Micrometer + Prometheus + Grafana
**Applies to:** ALL microservices

---

## 🎯 Objective
Full visibility into system health, JVM internals, and custom business KPIs (transaction throughput, failure rates, latency percentiles). Build dashboards that answer: *"Is the system healthy right now?"*

---

## 🏗️ Phase 6.1 — Actuator & Micrometer (Per Service)

### Dependencies (add to each service `pom.xml`)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Configuration (add to each `application.yml`)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

### What This Gives You (Out of the Box)
- JVM heap/non-heap memory usage
- Garbage collection counts and pause times
- HTTP request latency (p50, p95, p99)
- Active threads, CPU usage
- HikariCP connection pool stats (active, idle, pending)
- Kafka consumer lag (if Kafka consumer is present)

### Endpoint
- Each service exposes `GET /actuator/prometheus` → Prometheus-format metrics

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Boot in Action** | Ch. 6: Actuator — endpoint exposure, health indicators |
| **Site Reliability Engineering** | Ch. 6: Monitoring Distributed Systems — what to measure |

---

## 🏗️ Phase 6.2 — Custom Business Metrics

### Transaction Service — Custom Counters & Timers
```java
@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {
    private final MeterRegistry registry;

    public PaymentResponse pay(PaymentRequest req) {
        Timer.Sample sample = Timer.start(registry);
        try {
            // ... saga flow ...
            registry.counter("txn.total", "status", "success").increment();
            return response;
        } catch (Exception e) {
            registry.counter("txn.total", "status", "failed").increment();
            throw e;
        } finally {
            sample.stop(registry.timer("txn.latency"));
        }
    }
}
```

**Metrics produced:**
| Metric | Type | Tags | Purpose |
|--------|------|------|---------|
| `txn.total` | Counter | `status=success/failed` | Total transactions by outcome |
| `txn.latency` | Timer | — | End-to-end payment processing time (p50, p95, p99) |

### API Gateway — Rate Limiter Metrics
```java
registry.counter("gateway.ratelimit.rejected", "path", path).increment();
```

### Wallet Service — Balance Operations
```java
registry.counter("wallet.operations", "type", "credit").increment();
registry.counter("wallet.operations", "type", "debit").increment();
```

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Site Reliability Engineering** | Ch. 4: SLOs — define what "healthy" means numerically |
| **The Art of Scalability** | Ch. 12-14: Choosing the right metrics, avoiding high-cardinality traps |
| **Accelerate** | Ch. 2: DORA metrics for dev pipeline health |

---

## 🏗️ Phase 6.3 — Prometheus Setup

### Create `infra/prometheus/prometheus.yml`
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']

  - job_name: 'wallet-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8082']

  - job_name: 'transaction-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8083']

  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']

  - job_name: 'notification-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8084']
```

### Add to `docker-compose.yml`
```yaml
prometheus:
  image: prom/prometheus:latest
  container_name: prometheus
  ports:
    - "9090:9090"
  volumes:
    - ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
  extra_hosts:
    - "host.docker.internal:host-gateway"
```

### Verify
- Access `http://localhost:9090/targets` → all services should show `UP`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Site Reliability Engineering** | Ch. 14: Managing Incidents — alerting rules |
| **The DevOps Handbook** | Part III: Feedback loops, monitoring pipelines |

---

## 🏗️ Phase 6.4 — Grafana Dashboards

### Add to `docker-compose.yml`
```yaml
grafana:
  image: grafana/grafana:latest
  container_name: grafana
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_USER=admin
    - GF_SECURITY_ADMIN_PASSWORD=admin
  depends_on:
    - prometheus
```

### Setup Steps
1. Open `http://localhost:3000` (login: admin/admin)
2. Add Data Source → Prometheus → URL: `http://prometheus:9090`
3. Create dashboards

### Dashboard 1: System Health
| Panel | PromQL Query |
|-------|-------------|
| JVM Heap Used | `jvm_memory_used_bytes{area="heap"}` |
| CPU Usage | `system_cpu_usage` |
| Active DB Connections | `hikaricp_connections_active` |
| HTTP 5xx Errors | `http_server_requests_seconds_count{status=~"5.."}` |

### Dashboard 2: Business Metrics
| Panel | PromQL Query |
|-------|-------------|
| Txn Success Rate | `rate(txn_total{status="success"}[5m])` |
| Txn Failure Rate | `rate(txn_total{status="failed"}[5m])` |
| Txn Latency p99 | `histogram_quantile(0.99, rate(txn_latency_seconds_bucket[5m]))` |
| Rate Limiter Rejections | `rate(gateway_ratelimit_rejected_total[5m])` |

### Dashboard 3: Kafka Health
| Panel | PromQL Query |
|-------|-------------|
| Consumer Lag | `kafka_consumer_fetch_manager_records_lag` |
| Messages Consumed/sec | `rate(kafka_consumer_fetch_manager_records_consumed_total[5m])` |

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **DDIA** | Ch. 1: Reliability, Scalability, Maintainability — what metrics matter |
| **Site Reliability Engineering** | Ch. 4: SLOs — translate dashboard panels into SLO targets |
| **The DevOps Handbook** | Part III: Technical Practices of Feedback |

---

## ✅ Testing Checklist
- [ ] Each service exposes `/actuator/prometheus` with metrics
- [ ] Prometheus targets page (`localhost:9090/targets`) shows all services as UP
- [ ] Grafana connects to Prometheus data source
- [ ] System Health dashboard shows live JVM and DB pool data
- [ ] Make 10 payments → Business Metrics dashboard updates in real-time
- [ ] Trigger rate limiter → rejection counter increments on dashboard

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| Micrometer over raw Prometheus client | Spring-native; auto-instruments HTTP, JVM, Hikari, Kafka |
| 15s scrape interval | Balance between freshness and Prometheus storage load |
| `host.docker.internal` | Services run on host, Prometheus runs in Docker — this bridges them |
| Separate dashboards | System health vs business metrics serve different audiences (DevOps vs Product) |
