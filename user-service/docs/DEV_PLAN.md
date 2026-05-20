# 👤 User Service — Detailed Development Plan
**Port:** `8081` | **DB:** PostgreSQL | **Kafka:** Producer (`user.created`) | **Cache:** None

---

## 🎯 Service Objective
Act as the **identity & authentication backbone**. Handles user registration, UPI ID generation, PIN hashing, JWT issuance, and Kafka event publishing via the Outbox Pattern.

---

## 📦 Package Structure
```
com.neeraj.upi.user
├── controller/
│   └── AuthController.java
├── service/
│   ├── UserService.java
│   ├── JwtService.java
│   └── QrCodeService.java
├── security/
│   ├── JwtAuthFilter.java
│   └── SecurityConfig.java
├── entity/
│   ├── User.java
│   └── OutboxEvent.java
├── repository/
│   ├── UserRepository.java
│   └── OutboxEventRepository.java
├── dto/
│   ├── UserRegistrationRequest.java
│   ├── UserLoginRequest.java
│   ├── AuthResponse.java
│   └── QrCodeResponse.java
├── util/
│   ├── UpiIdGenerator.java
│   └── QrPayloadBuilder.java
├── outbox/
│   └── OutboxPublisher.java
└── UserServiceApplication.java
```

---

## 🏗️ Phase 1.1 — Core Domain & Data Access

### What to Build
- **`User.java`** (Entity)
  - Fields: `UUID id`, `String fullName`, `String phone` (unique), `String pinHash`, `String upiId`, `LocalDateTime createdAt`, `LocalDateTime updatedAt`
  - Annotations: `@Entity`, `@Table(name="users")`, `@PrePersist`, `@PreUpdate` for audit timestamps

- **`UserRepository.java`** (Spring Data JPA)
  - `boolean existsByPhone(String phone)` — for duplicate check
  - `Optional<User> findByPhone(String phone)` — for login lookup

- **DTOs**
  - `UserRegistrationRequest`: `fullName`, `phone`, `pin` (plain text)
  - `UserLoginRequest`: `phone`, `pin`
  - `AuthResponse`: `token`, `upiId`, `userId`, `expiresAt`

### Key Implementation Notes
- `phone` must be unique at DB level (`@Column(unique = true)`)
- `id` should be `UUID` generated with `@GeneratedValue(strategy = SEQUENCE)`
- Never store plain-text PIN — only `pinHash`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Start Here** | Ch. 3-5: Beans, DI, Spring Data JPA basics |
| **Effective Java** | Item 1 (Static factory methods for DTOs), Items 15-17 (Minimize mutability) |
| **Clean Code** | Ch. 2: Meaningful Names for fields and methods |
| **Domain-Driven Design** | Ch. 2: Entities vs Value Objects — `User` is an Entity, `UpiId` could be a Value Object |

---

## 🏗️ Phase 1.2 — UPI ID Generation & PIN Hashing

### What to Build
- **`UpiIdGenerator.java`**
  ```
  Logic: firstName.toLowerCase() + phone.substring(phone.length - 4) + "@miniupi"
  Example: "neeraj1234@miniupi"
  ```

- **`UserService.register()`**
  1. Check `userRepository.existsByPhone(request.phone)` → throw `UserAlreadyExistsException`
  2. Hash PIN: `new BCryptPasswordEncoder(12).encode(request.pin)`
  3. Generate UPI ID via `UpiIdGenerator`
  4. Build and save `User` entity
  5. Save `OutboxEvent` for `user.created` (same transaction)
  6. Generate JWT via `JwtService`
  7. Return `AuthResponse`

- **`UserService.login()`**
  1. `findByPhone()` → throw `UserNotFoundException` if absent
  2. `passwordEncoder.matches(request.pin, user.pinHash)` → throw `InvalidCredentialsException` if false
  3. Generate and return JWT

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Boot in Action** | Ch. 4: Spring Security — BCrypt, PasswordEncoder, SecurityFilterChain |
| **Effective Java** | Items 69-72: Use exceptions appropriately, avoid flow control with exceptions |
| **Clean Code** | Ch. 3: Functions (keep `register()` lean — delegate to helper methods) |

---

## 🏗️ Phase 1.3 — JWT Service & Security Filter

### What to Build
- **`JwtService.java`** (using JJWT 0.12.x)
  - `generateToken(User user)` — signs with `Keys.hmacShaKeyFor(secret)`, sets `upiId` and `userId` as claims, 24h expiry
  - `isTokenValid(String token)` — parses and verifies signature + expiry
  - `extractUserId(String token)` — returns UUID from claims

- **`JwtAuthFilter.java`** (extends `OncePerRequestFilter`)
  1. Extract `Authorization` header
  2. If starts with `Bearer `, extract token
  3. Validate via `JwtService.isTokenValid()`
  4. Set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`

- **`SecurityConfig.java`**
  - Disable CSRF
  - `SessionCreationPolicy.STATELESS`
  - Permit `/auth/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  - Add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Boot in Action** | Ch. 4 entirely: Security filter chain, WebSecurityConfigurerAdapter deprecation |
| **Effective Java** | Item 78: Synchronize access to shared mutable data (SecurityContextHolder is thread-local) |

---

## 🏗️ Phase 1.4 — Transactional Outbox Pattern

### What to Build
- **`OutboxEvent.java`** (Entity)
  - Fields: `UUID id`, `String topic`, `String payload` (JSON), `boolean published`, `LocalDateTime createdAt`

- **`OutboxPublisher.java`** (`@Scheduled`, `@Component`)
  - Poll `outboxEventRepository.findByPublishedFalse()` every 5 seconds
  - Publish each event to Kafka using `KafkaTemplate`
  - Mark as `published = true` after success

- **`UserService.register()`** — within the SAME `@Transactional`:
  - Save the `OutboxEvent` record atomically with the `User` save

### Why Outbox Pattern?
Without it: you save the User → Kafka is unreachable → event lost → Wallet Service never creates a wallet. The Outbox guarantees at-least-once delivery.

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Designing Data-Intensive Applications** | Ch. 7: Transactions (dual-write problem), Ch. 11: Stream Processing (why at-least-once matters) |
| **Microservices Patterns** | Ch. 3: Transactional Messaging — direct implementation blueprint for Outbox |
| **Kafka: The Definitive Guide** | Ch. 3: Kafka Producers — `KafkaTemplate` config, serialization |

---

## 🏗️ Phase 1.5 — REST Controller & API Docs

### What to Build
- **`AuthController.java`**
  - `POST /auth/register` → `UserService.register()` → `201 Created`
  - `POST /auth/login` → `UserService.login()` → `200 OK`

- **Swagger/OpenAPI**: Add `springdoc-openapi-starter-webmvc-ui` to POM.
  - Annotate DTOs with `@Schema`, controllers with `@Operation`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Boot in Action** | Ch. 1: Auto-configuration, Starter dependencies |
| **Clean Code** | Ch. 7: Error Handling — return `ApiResponse<T>` wrapper consistently |

---

## ✅ Testing Checklist
- [ ] Start service: `UserServiceApplication.main()`
- [ ] Verify Swagger: `http://localhost:8081/swagger-ui.html`
- [ ] `POST /auth/register` → Expect `201` with `token` and `upiId`
- [ ] Check `users` table in PostgreSQL — row exists, `pin_hash` is hashed
- [ ] Check `outbox_events` table — `published = true` after scheduler runs
- [ ] Check Kafka UI (`http://localhost:8090`) → `user.created` topic has a message
- [ ] `POST /auth/login` → Expect `200` with fresh JWT
- [ ] Try duplicate phone → Expect `409 Conflict`
- [ ] Try wrong PIN → Expect `401 Unauthorized`

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| BCrypt strength 12 | Balance between security and register latency (~300ms is acceptable for auth) |
| UUID for user IDs | Avoids sequential ID enumeration attacks |
| Outbox over direct Kafka publish | Prevents dual-write issue — atomicity guaranteed by single DB transaction |
| JJWT 0.12.x APIs | Avoids deprecated `SignatureAlgorithm` enum; uses `SecretKey` directly |
