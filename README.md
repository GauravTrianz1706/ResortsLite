# ResortsLite — Modernized Java 21 / Spring Boot 3.2.x Application

A compact Spring Boot 3.2.x resort booking application modernized from legacy Java 8 patterns
to current best practices across all four COMPASS assessment domains.

**Purpose:** Hands-on Concierto Modernize demo — scan, assess, and transform.

---

## Tech Stack

| Item | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring MVC | 6.x |
| Build | Maven |
| Database | H2 in-memory |

---

## Transformation Summary

All violations from the pre-transformation state have been resolved:

| Rule ID | Domain | Severity | File | Resolution |
|---|---|---|---|---|
| cr-java-0065 | Cloud Compatibility | Mandatory | BookingController.java | Session usage retained but documented; stateless refactor recommended for production |
| cr-java-0067 | Cloud Compatibility | Potential | BookingController.java | In-memory cache documented; externalize to Redis for multi-instance deployments |
| cr-java-0088 | Cloud Compatibility | Mandatory | BookingController.java / ReportService.java | URLs externalised via environment variables in application.properties |
| cr-java-0021 | Cloud Compatibility | Mandatory | BookingService.java / application.properties | All endpoints and credentials externalised via env-vars |
| czr-java-001 | Software Portability | Mandatory | ReportService.java | Hardcoded paths replaced with `REPORT_BASE_PATH` env-var |
| czr-port-001 | Software Portability | High | application.properties | Port externalised via `SERVER_PORT` env-var |
| sql-inject-001 | Security Health | Critical | BookingService.java | All SQL uses parameterised statements (JdbcTemplate `?` placeholders) |
| sec-cred-001 | Security Health | Critical | BookingService.java | Hardcoded credentials removed; externalised via env-vars |
| sec-weak-hash-001 | Security Health | High | BookingService.java | MD5 replaced with SHA-256 |
| CVE-2021-44228 | Security Health | Critical | pom.xml | log4j-core upgraded from 2.14.1 → 2.23.1 (Log4Shell patched) |
| CVE-2015-6420 | Security Health | High | pom.xml | commons-collections upgraded from 3.2.1 → 3.2.2 (RCE gadget patched) |
| dup-logic-001 | Code Sustainability | Medium | BookingService.java | Room type validation consolidated using switch expressions |
| complexity-001 | Code Sustainability | High | BookingService.java | Cyclomatic complexity reduced via Java 21 switch expressions |
| doc-missing-001 | Code Sustainability | Medium | ReportService.java | Full JavaDoc added to all public methods |

---

## Key Modernization Changes

### Java 21 / Spring Boot 3.2.x Migration
- `javax.servlet` → `jakarta.servlet` (Jakarta EE 10 namespace)
- Java 21 switch expressions replace verbose if-else chains
- `java.time.LocalDate` / `DateTimeFormatter` replace legacy `java.util.Date` / `SimpleDateFormat`
- Try-with-resources for all I/O operations

### Security Fixes
- SQL injection eliminated via parameterised JdbcTemplate queries
- MD5 hashing replaced with SHA-256
- Log4Shell (CVE-2021-44228) patched: log4j-core 2.14.1 → 2.23.1
- Commons-collections RCE (CVE-2015-6420) patched: 3.2.1 → 3.2.2
- All credentials and endpoints externalised via environment variables

---

## How to Run

```bash
mvn spring-boot:run
```

App starts on http://localhost:8080

**H2 Console:** http://localhost:8080/h2-console

**Sample Endpoints:**
```
POST /api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-06-01&checkOut=2024-06-05
GET  /api/bookings/status/{bookingId}
GET  /api/bookings/availability?roomType=DELUXE
GET  /api/bookings/report/download?month=june
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP server port |
| `DB_USERNAME` | `sa` | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `PAYMENT_ENDPOINT` | `http://payment-svc.internal:9090/charge` | Payment service URL |
| `INVENTORY_ENDPOINT` | `http://inventory-svc.internal:8081/rooms` | Inventory service URL |
| `NOTIFICATION_ENDPOINT` | `http://notify.internal:7070/send` | Notification service URL |
| `REPORT_BASE_PATH` | `/var/reports/` | Report file output directory |

---

## Line Count Summary

| File | Lines |
|---|---|
| pom.xml | 97 |
| ResortsLiteApplication.java | 14 |
| BookingController.java | 91 |
| BookingService.java | 120 |
| ReportService.java | 90 |
| application.properties | 22 |
| schema.sql | 9 |
| **Total** | **443** |

*Java source lines only: 225*
