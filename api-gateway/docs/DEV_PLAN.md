# 🛡️ API Gateway — Detailed Development Plan
**Port:** `8080` | **DB:** None | **Kafka:** None | **Cache:** Redis (Rate Limiter)

---

## 🎯 Service Objective
**Single entry point** for all client requests. Routes traffic to downstream services, validates JWTs at the edge, and enforces Redis-backed token-bucket rate limiting.

---

## 📦 Package Structure
```
com.neeraj.upi.gateway
├── config/
│   └── GatewayConfig.java
├── filter/
│   └── JwtAuthFilter.java
└── GatewayApplication.java
```

---

## 🏗️ Phase 5.1 — Route Configuration

### `application.yml`
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/auth/**
        - id: wallet-service
          uri: http://localhost:8082
          predicates:
            - Path=/wallet/**
        - id: transaction-service
          uri: http://localhost:8083
          predicates:
            - Path=/transactions/**
```

### Key Notes
- Gateway runs on **Spring WebFlux** (reactive, non-blocking) — NOT Spring MVC
- All downstream services remain on Spring MVC; only Gateway is reactive

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Start Here** | Ch. 15: Reactive with WebFlux — Mono/Flux fundamentals |
| **Building Microservices** | Ch. 13: At Scale — API gateway patterns |

---

## 🏗️ Phase 5.2 — Redis Rate Limiter

### `GatewayConfig.java`
```java
@Bean
public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
    );
}
```

### Rate Limiter in `application.yml`
```yaml
spring.cloud.gateway.routes:
  - id: transaction-service
    uri: http://localhost:8083
    predicates:
      - Path=/transactions/**
    filters:
      - name: RequestRateLimiter
        args:
          redis-rate-limiter.replenishRate: 10   # 10 req/sec
          redis-rate-limiter.burstCapacity: 20    # burst up to 20
          key-resolver: "#{@ipKeyResolver}"
```

### Dependencies
- `spring-boot-starter-data-redis-reactive`
- `spring-cloud-starter-gateway`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **System Design Interview** | Ch. 4: Rate Limiter Design — token bucket vs leaky bucket |
| **The Art of Scalability** | Ch. 9-11: Capacity planning |
| **DDIA** | Ch. 12: Rate limiting theory |

---

## 🏗️ Phase 5.3 — JWT Edge Validation

### `JwtAuthFilter.java` (implements `GatewayFilter`)

**Flow:**
1. Check if path is in `PUBLIC_PATHS` (`/auth/register`, `/auth/login`) → skip validation
2. Extract `Authorization` header
3. If missing or not `Bearer ` prefix → return `401 UNAUTHORIZED`
4. Validate JWT signature using same secret as User Service
5. If valid → forward request downstream with `X-User-Id` header injected
6. If invalid/expired → return `401 UNAUTHORIZED`

### Public Paths List
```java
private static final List<String> PUBLIC_PATHS = List.of(
    "/auth/register",
    "/auth/login",
    "/actuator/health",
    "/actuator/prometheus"
);
```

### Important: Reactive Filter
- This is a **reactive** filter — uses `ServerWebExchange`, NOT `HttpServletRequest`
- Return type is `Mono<Void>`, not void
- Error responses use `exchange.getResponse().setStatusCode()` + `exchange.getResponse().setComplete()`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Boot in Action** | Ch. 4: Security — JWT filter concepts (adapt for reactive) |
| **Building Microservices** | Ch. 9: Security — edge validation, token propagation |

---

## ✅ Testing Checklist
- [ ] Start Gateway on port 8080 (all downstream services must be running)
- [ ] `POST http://localhost:8080/auth/register` → routes to User Service, returns JWT
- [ ] `POST http://localhost:8080/transactions/pay` without token → `401`
- [ ] Same request with valid `Bearer` token → routes to Transaction Service
- [ ] Fire 30 rapid requests → rate limiter returns `429 Too Many Requests`
- [ ] Stop using individual service ports — ALL requests go through `:8080`

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| Spring Cloud Gateway (reactive) | Non-blocking I/O handles thousands of concurrent connections on fewer threads |
| JWT validated at edge | Downstream services trust the Gateway; avoids redundant validation per service |
| IP-based rate limiting | Simple MVP — upgrade to user-ID-based rate limiting after auth is stable |
| Redis for rate limiter state | Distributed rate limiting works across multiple Gateway instances |
