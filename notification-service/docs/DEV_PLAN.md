# 🔔 Notification Service — Detailed Development Plan
**Port:** `8084` | **DB:** None | **Kafka:** Consumer (`txn.completed`, `txn.failed`) | **Cache:** None

---

## 🎯 Service Objective
Purely **reactive and asynchronous**. Listens to Kafka transaction events and dispatches simulated SMS/Email alerts to sender and receiver. No database, no REST API — it's a fire-and-forget consumer.

---

## 📦 Package Structure
```
com.neeraj.upi.notification
├── listener/
│   └── TransactionEventListener.java
├── service/
│   └── NotificationService.java
├── dto/
│   ├── TransactionCompletedEvent.java
│   └── TransactionFailedEvent.java
└── NotificationServiceApplication.java
```

---

## 🏗️ Phase 4.1 — Event DTOs

### `TransactionCompletedEvent.java`
- Fields: `UUID txnId`, `String senderUpi`, `String receiverUpi`, `BigDecimal amount`, `LocalDateTime timestamp`

### `TransactionFailedEvent.java`
- Fields: `UUID txnId`, `String senderUpi`, `String receiverUpi`, `BigDecimal amount`, `String failureReason`, `LocalDateTime timestamp`

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Domain-Driven Design** | Ch. 8: Domain Events — structure event payloads correctly |

---

## 🏗️ Phase 4.2 — Kafka Consumer

### `TransactionEventListener.java`
```java
@KafkaListener(topics = "txn.completed", groupId = "notification-group")
public void onTransactionCompleted(TransactionCompletedEvent event) {
    notificationService.sendDebitAlert(event.getSenderUpi(), event.getAmount(), event.getTxnId());
    notificationService.sendCreditAlert(event.getReceiverUpi(), event.getAmount(), event.getTxnId());
}

@KafkaListener(topics = "txn.failed", groupId = "notification-group")
public void onTransactionFailed(TransactionFailedEvent event) {
    notificationService.sendFailureAlert(event.getSenderUpi(), event.getAmount(), event.getFailureReason());
}
```

### Consumer Config (`application.yml`)
```yaml
spring:
  kafka:
    consumer:
      group-id: notification-group
      auto-offset-reset: earliest
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.neeraj.upi.*"
```

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Kafka: The Definitive Guide** | Ch. 4: Consumers — group coordination, partition assignment |
| **Kafka: The Definitive Guide** | Ch. 6: Reliable Delivery — at-least-once guarantees |
| **Building Microservices** | Ch. 11: At Scale — consumer lag, graceful shutdown |

---

## 🏗️ Phase 4.3 — Notification Service (Mock Dispatch)

### `NotificationService.java`
Uses SLF4J logging to simulate real SMS/Email delivery:

```java
@Slf4j
@Service
public class NotificationService {

    public void sendDebitAlert(String upiId, BigDecimal amount, UUID txnId) {
        log.info("📤 SMS → {}: Your a/c is DEBITED ₹{}. Txn ID: {}",
                 upiId, amount, txnId);
    }

    public void sendCreditAlert(String upiId, BigDecimal amount, UUID txnId) {
        log.info("📥 SMS → {}: Your a/c is CREDITED ₹{}. Txn ID: {}",
                 upiId, amount, txnId);
    }

    public void sendFailureAlert(String upiId, BigDecimal amount, String reason) {
        log.info("❌ SMS → {}: Payment of ₹{} FAILED. Reason: {}",
                 upiId, amount, reason);
    }
}
```

### Future Enhancement Path
- Replace `log.info` with real SMS gateway (e.g., Twilio) or email (JavaMail)
- Add `@Async` for non-blocking dispatch
- Add a `notification_log` table if audit trail is needed later

### 📚 Read Before Coding
| Book | What to Read |
|------|-------------|
| **Spring Start Here** | Ch. 14: Spring Events & Async processing |
| **Clean Code** | Ch. 3: Functions — keep alert methods small and focused |

---

## ✅ Testing Checklist
- [ ] Start Notification Service (Kafka must be running)
- [ ] Make a successful payment via Transaction Service
- [ ] Watch console logs → DEBIT alert for sender, CREDIT alert for receiver
- [ ] Make a failed payment (e.g., insufficient funds)
- [ ] Watch console logs → FAILURE alert for sender
- [ ] Send same Kafka event twice → alerts fire again (at-least-once is acceptable here)

---

## 🔑 Key Design Decisions
| Decision | Rationale |
|----------|-----------|
| No database | Notifications are stateless and ephemeral — no persistence needed for MVP |
| Mock via logging | Real SMS/Email is out of scope; logs prove the pipeline works end-to-end |
| Separate consumer group | `notification-group` is independent of `wallet-service-group` — no interference |
| At-least-once is OK | Duplicate SMS is better than missed SMS for a payment system |
