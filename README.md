# TradeReport

Spring Boot service for creating and updating trade reports. Every change produces a
`TradeReportEvent` on Kafka, and a consumer turns each event into an **append-only audit row** in Postgres.
Built to scale horizontally: run as many instances as you like.

## Architecture

```
POST /createNewReport ─┐
PUT  /updateReport    ─┤   one DB transaction
                       ▼
        ┌──────────────────────────┐
        │ trade_report (the change)│
        │ outbox_event (the event) │
        └────────────┬─────────────┘
                     │ OutboxRelay (scheduled, every instance, SKIP LOCKED)
                     ▼
          Kafka topic: trade-report-events   (key = reportId, 12 partitions)
                     │ consumer group: trade-report-audit
                     ▼
          TradeReportConsumer ──► trade_reports_audits (unique event_id)
                     │ on repeated failure
                     ▼
          trade-report-events.DLT   (dead-letter topic)
```

| Piece | What it does |
|---|---|
| Outbox | The report change and its event commit together, so a committed change is never lost before reaching Kafka. |
| `OutboxRelay` | Publishes unpublished outbox rows to Kafka. Instances claim different rows (`FOR UPDATE SKIP LOCKED`). |
| `TradeReportConsumer` | Inserts one audit row per event. The unique `event_id` makes redelivery harmless (idempotent). |
| DLT | Messages that keep failing (or can't be deserialized) are parked on `trade-report-events.DLT` instead of blocking a partition. |
| DB trigger | `UPDATE` and `DELETE` on `trade_reports_audits` are rejected, so audits are immutable. |

### Trade-offs to know
- Audits are **eventually consistent**: a read right after a write may not show its audit yet.
- Delivery is **at-least-once**; duplicates are absorbed by the unique `event_id`.
- Events for one report can arrive slightly out of order if different instances relay them. Order audit history by `report_version`.
- Parallelism is capped by partition count (12 by default): at most 12 consumer threads do work at once across all instances.

## Prerequisites
- Java 21+ (tested on 24)
- Docker Desktop (running)
- No separate Maven install needed; use the included `./mvnw`

## Run it

All commands are from the project folder.

### 1. Start Postgres and Kafka
```bash
docker compose up -d postgres kafka
docker compose ps
```
Wait until both show `healthy`.

### 2. Start the app
```bash
./mvnw spring-boot:run
```
On startup, Flyway creates the tables and the app creates the Kafka topics.

### 3. Try it
```bash
curl -X POST localhost:8080/CreateNewReport \
  -H 'Content-Type: application/json' \
  -d '{"amount":10.50,"firm":"firmA","tradeRefId":"REF-1","user":"ellis"}'

curl -X PUT localhost:8080/updateReport \
  -H 'Content-Type: application/json' \
  -d '{"reportId":1,"amount":99.99,"user":"bob"}'

curl localhost:8080/getReport/1
curl 'localhost:8080/getReports?limit=10'
curl localhost:8080/getReportAudits/1
```
A second or so later, each change has produced one row in `trade_reports_audits`, which `getReportAudits` returns.

`getReports` returns the newest reports first (default 50, at most 200). `getReportAudits/{id}` returns one
report's audit history, oldest version first, or a 404 if the report does not exist.

A web dashboard lives in the sibling `TradeReportClient` folder (Angular, runs in Docker on port 4200).
Browsers only let a page call this API if the API allows the page's origin (CORS). Allowed origins come from
`CORS_ALLOWED_ORIGINS`, default `http://localhost:4200`.

### 4. Run a second instance (scale-out demo)
In another terminal:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
Both instances join the `trade-report-audit` group and Kafka splits the 12 partitions between them
(look for `partitions assigned` in the logs, or check Kafka UI).

## Web UIs (optional)
```bash
docker compose --profile ui up -d
```

| Tool | URL | Notes |
|---|---|---|
| Kafka UI | http://localhost:8090 | Topics, messages, consumer group lag, DLT |
| pgAdmin | http://localhost:5050 | No login. Server "TradeReport (local)" is pre-registered; password is `tradereport` (tick Save Password). Right-click the `tradereport` database, then Query Tool. |

## Database access

| Setting | Value |
|---|---|
| Host / Port | `localhost` / `5432` |
| Database | `tradereport` |
| User / Password | `tradereport` / `tradereport` (local only) |

Port 5432 speaks the Postgres protocol, not HTTP, so a browser can't open it. Use pgAdmin, IntelliJ's Database tool, DBeaver, or psql:
```bash
docker exec -it tradereport-postgres-1 psql -U tradereport -d tradereport
```

Useful queries:
```sql
select * from trade_report;
select * from outbox_event order by id;                 -- published_at set = sent to Kafka
select * from trade_reports_audits order by id;         -- the audit trail
select count(*), count(distinct event_id) from trade_reports_audits;  -- should match
```

## Configuration

Set as environment variables (defaults in `application.properties`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tradereport` | Database |
| `DB_USER` / `DB_PASSWORD` | `tradereport` / `tradereport` | Database login |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka brokers |
| `KAFKA_PARTITIONS` | `12` | Topic partitions (can only be increased) |
| `KAFKA_REPLICAS` | `1` | Use 3 in a real cluster |
| `CONSUMER_CONCURRENCY` | `3` | Consumer threads per instance |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Browser origins allowed to call the API (comma separated) |

Outbox relay: `app.outbox.relay.enabled`, `app.outbox.relay.poll-ms` (500), `app.outbox.relay.batch-size` (100).

## Tests
```bash
./mvnw test
```
Tests use in-memory H2 with Flyway and the Kafka listener disabled, so Docker isn't needed.
They cover audit idempotency (a duplicate delivery creates one row) and that creating a report writes an
outbox row but no synchronous audit.

## Stopping and resetting
```bash
docker compose --profile ui down        # stop containers, keep data
docker compose --profile ui down -v     # stop and DELETE all data (Postgres, pgAdmin settings)
```

## Troubleshooting
- **`docker compose` says unknown command:** open Docker Desktop and wait for it to finish starting.
- **App can't connect to Postgres or Kafka:** run `docker compose ps` and check both are `healthy`.
- **Messages in `trade-report-events.DLT`:** inspect them in Kafka UI. Typical causes are messages from before `eventId` existed, or an event whose report row is missing.
- **Changed partition count on an existing topic:** this changes which partition each key maps to. Do it at a quiet time or use a new topic.

## Known limitations
- `cancelReport` in `TradeReportService` is incomplete (it saves nothing and isn't exposed by the controller).
- Local credentials are for development only; use real secrets and a managed Postgres/Kafka elsewhere.
