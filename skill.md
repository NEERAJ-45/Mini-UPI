---
name: clean-production-coding
version: 1.0.0
description: Standards and practices for writing clean, maintainable, and production-ready code.
tags:
  - coding
  - clean-code
  - production
  - best-practices
author: AI Assistant
---

# 🧠 Skill: Clean & Production-Style Coding

## Overview

This skill defines the standards, conventions, and practices the AI assistant must follow to produce **clean, maintainable, and production-ready code** across every file it generates or modifies.

---

## 🎯 Core Principles

1. **Clarity over cleverness** — Code must be immediately understandable by another developer.
2. **Fail loudly** — Errors should surface early, loudly, and with context. Never silently swallow exceptions.
3. **Single Responsibility** — Every function, class, and module does one thing well.
4. **Immutability by default** — Prefer `const` over `let`, frozen objects, read-only parameters where applicable.
5. **Explicit over implicit** — Avoid magic numbers, side-effectful defaults, and hidden state.

---

## 📁 Project & File Structure

- Organize code by **feature/domain**, not by file type.
- Keep files **small and focused** — aim for < 300 lines per file; split if it grows beyond that.
- Use consistent naming conventions per language:

| Language | Files | Variables | Constants | Classes |
|---|---|---|---|---|
| JavaScript/TypeScript | `kebab-case.ts` | `camelCase` | `UPPER_SNAKE_CASE` | `PascalCase` |
| Python | `snake_case.py` | `snake_case` | `UPPER_SNAKE_CASE` | `PascalCase` |
| Go | `snake_case.go` | `camelCase` | `PascalCase` | `PascalCase` |
| Java | `PascalCase.java` | `camelCase` | `UPPER_SNAKE_CASE` | `PascalCase` |

- **Never commit** `.env`, secrets, or credentials — use `.env.example` templates.

---

## ✍️ Code Style Rules

### General
- Max line length: **100 characters** (120 for Go/Java).
- Use **2-space** indentation for JS/TS/JSON; **4-space** for Python/Java/Go.
- Always add a **trailing newline** at end of file.
- Remove all **dead code**, unused imports, and commented-out blocks before finishing.

### Functions & Methods
```
✅ DO:
  - Keep functions under 30 lines
  - Use descriptive verb-noun names: getUserById(), parseInvoice()
  - Return early (guard clauses) instead of deeply nesting
  - Accept typed parameters; never use `any` in TypeScript

❌ DON'T:
  - Use `data`, `info`, `temp`, `x`, `foo` as variable names
  - Mutate function arguments directly
  - Return different types from the same function
  - Use boolean flags to change function behavior — split into two functions
```

### Example — Guard Clauses (Preferred)
```typescript
// ❌ BAD
function processPayment(payment: Payment) {
  if (payment) {
    if (payment.amount > 0) {
      if (payment.status === 'pending') {
        // ... actual logic
      }
    }
  }
}

// ✅ GOOD
function processPayment(payment: Payment): void {
  if (!payment) throw new Error('Payment is required');
  if (payment.amount <= 0) throw new Error('Payment amount must be positive');
  if (payment.status !== 'pending') throw new Error(`Invalid payment status: ${payment.status}`);

  // ... actual logic, clean and flat
}
```

---

## 🔒 Error Handling

- **Always** wrap external I/O (DB calls, HTTP, file system) in try/catch or Result types.
- Include context in error messages: *what* failed, *where*, and *what input* caused it.
- Use **custom error classes** for domain errors (e.g., `PaymentNotFoundError`, `UnauthorizedError`).
- Never expose raw stack traces or internal details to API consumers.

```typescript
// ✅ GOOD — Custom error with context
export class PaymentNotFoundError extends Error {
  constructor(paymentId: string) {
    super(`Payment not found: id=${paymentId}`);
    this.name = 'PaymentNotFoundError';
  }
}
```

---

## 🗄️ Database & Persistence

- Always use **parameterized queries** — never string-concatenate SQL.
- Wrap multi-step DB operations in **transactions**.
- Add **indexes** for all foreign keys and frequently queried columns.
- Use **migrations** for schema changes — never alter production DBs manually.
- Validate data **before** writing to the DB, not after.

---

## 🌐 API Design (REST / GraphQL)

- Use **nouns** for REST endpoints: `/payments`, `/users/{id}/invoices`.
- Return **consistent response envelopes**:
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": { "page": 1, "total": 42 }
}
```
- Use correct **HTTP status codes**: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `409 Conflict`, `500 Internal Server Error`.
- Always **validate and sanitize** request inputs at the API boundary.
- Version your APIs: `/api/v1/...`.

---

## 🔐 Security

- Sanitize **all** user inputs.
- Hash passwords with **bcrypt** (cost factor ≥ 12) or **Argon2**.
- Use **short-lived JWTs** (≤ 15 min access tokens) with refresh token rotation.
- Set **rate limiting** on all public endpoints.
- Never log sensitive fields: passwords, tokens, card numbers, SSNs.
- Apply **principle of least privilege** to all service accounts and DB roles.

---

## 🧪 Testing Standards

- Write tests **alongside** the code, not after.
- Minimum coverage targets: **80% unit**, **60% integration** for critical paths.
- Use **AAA pattern**: Arrange → Act → Assert.
- Name tests descriptively:
  ```
  it('should return 404 when payment does not exist')
  it('throws PaymentNotFoundError for unknown paymentId')
  ```
- Mock **all** external dependencies in unit tests (DB, HTTP clients, queues).
- Write at least one **happy path** and one **failure path** test per function.

---

## 📝 Comments & Documentation

- Code should be **self-documenting** — use clear names over comments.
- Write comments for **"why"**, not **"what"**:
  ```typescript
  // ❌ BAD: Increment counter
  count++;

  // ✅ GOOD: Retry limit reached; abort to prevent infinite loop
  if (retryCount >= MAX_RETRIES) return;
  ```
- Add **JSDoc / docstrings** for all public functions, classes, and interfaces.
- Keep a `CHANGELOG.md` updated for every release.

---

## 🚀 Performance

- Avoid **N+1 queries** — use joins, eager loading, or batched queries.
- Use **pagination** for all list endpoints (default page size ≤ 100).
- Cache expensive, rarely-changing computations with a clear **TTL strategy**.
- Use **async/await** everywhere — never block the event loop.
- Profile before optimizing — don't prematurely optimize.

---

## 📦 Dependencies

- Pin **exact versions** in production (`package-lock.json`, `poetry.lock`).
- Audit dependencies regularly: `npm audit`, `pip-audit`, `trivy`.
- Prefer **well-maintained, minimal** packages over bloated ones.
- Never import a library just for one trivial utility — implement it inline.

---

## 🔄 Git & Version Control

- Use **Conventional Commits**:
  ```
  feat(payments): add webhook retry logic
  fix(auth): resolve token expiry race condition
  chore(deps): bump axios to 1.7.2
  refactor(user-service): extract validation to helper
  ```
- Keep PRs **small and focused** — one feature/fix per PR.
- Always **rebase** before merging — keep history linear.
- Never force-push to `main` or `master`.
- Write meaningful PR descriptions with context and testing notes.

---

## ⚙️ Environment & Configuration

- Use `.env` files locally; **secret managers** (AWS SSM, Vault, GCP Secret Manager) in production.
- Always validate required env vars at **startup**, fail fast if missing:
  ```typescript
  const PORT = parseInt(process.env.PORT ?? '', 10);
  if (isNaN(PORT)) throw new Error('PORT env variable is required and must be a number');
  ```
- Separate configs per environment: `dev`, `staging`, `production`.

---

## 🏗️ Architecture Guardrails

- Prefer **dependency injection** over hard-coded singletons.
- Define **interfaces/contracts** before implementations.
- Decouple business logic from transport layer (HTTP, queues, CLI).
- Avoid circular dependencies — enforce via linting rules (`import/no-cycle`).
- Document system boundaries and data flows in `docs/architecture.md`.

---

## ✅ Pre-Commit / CI Checklist

Before any code is merged, the following must pass:

- [ ] Linter passes (`eslint`, `flake8`, `golangci-lint`)
- [ ] Formatter applied (`prettier`, `black`, `gofmt`)
- [ ] All tests pass
- [ ] No `console.log` / `print` / debug statements left in code
- [ ] No hardcoded credentials or secrets
- [ ] API contracts updated if endpoints changed
- [ ] ENV example file updated if new env vars were added
- [ ] CHANGELOG updated

---

> **Rule of Thumb**: Write code as if the person maintaining it is a brilliant developer who has zero context about why you made a decision — leave a trail of breadcrumbs through clear naming, structure, and focused comments.
