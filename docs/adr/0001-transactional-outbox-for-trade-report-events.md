# ADR 0001: Transactional outbox for trade report events

- **Status:** Accepted
- **Date:** 2026-11-19
- **Branch:** `feat-3-kafka-audit-consumer`

## Context

Every create or update of a `TradeReport` must produce an audit row. Regulators require the audit
trail to be complete and immutable, so a committed change that has no audit is a defect.

The first design wrote the audit row synchronously in `TradeReportService` and published a Kafka
event through `TradeReportEventPublisher`. It had these problems:

- The service called `sendMessage(...)` directly, so `@TransactionalEventListener(AFTER_COMMIT)` never fired.
  In `updateAmountAndFirm` the Kafka send happened **inside** the transaction, so a rollback could leave a
  published event for a change that never happened. `createNewReport` was not transactional at all.
- The send happened after the database write, so a crash between the two lost the event.
- The consumer only logged, so nothing real was decoupled from the request path.

We needed audits written asynchronously by a consumer that scales horizontally, without ever losing an event.

## Decision

Use the **transactional outbox pattern**.

1. `TradeReportService` writes the report change and an `outbox_event` row **in the same database
   transaction**. It does not touch Kafka and does not write audits.
2. `OutboxRelay` (scheduled, runs on every instance) claims unpublished rows with
   `FOR UPDATE SKIP LOCKED`, publishes them to `trade-report-events`, and marks them published.
3. `TradeReportConsumer` (one consumer group, `trade-report-audit`) inserts one row into
   `trade_reports_audits` per event. `event_id` is `UNIQUE`, so a redelivered event cannot create a second
   audit row.
4. Failures are retried with backoff, then sent to `trade-report-events.DLT`.
5. A database trigger rejects `UPDATE` and `DELETE` on `trade_reports_audits`.

```
service --(1 txn)--> trade_report + outbox_event
                          |  OutboxRelay (every instance, SKIP LOCKED)
                          v
                 trade-report-events (key = reportId, 12 partitions)
                          |  group: trade-report-audit
                          v
                 TradeReportConsumer --> trade_reports_audits (unique event_id)
                          |  repeated failure
                          v
                 trade-report-events.DLT
```

## Why an outbox and not direct publishing

Writing to the database and sending to Kafka are two systems, so they cannot commit atomically (the dual write
problem). Direct publishing has two failure modes:

| Approach | Failure |
|---|---|
| Publish before commit | The transaction rolls back after the event is sent. A **ghost event** exists for a change that never happened. |
| Publish after commit | The app crashes or Kafka is down after the commit. The change is saved and its **event is lost**, so the audit is silently missing. |

The outbox row commits with the business change, so either both exist or neither does. Once committed, the
event stays in the table until it has been published, however long Kafka was unavailable.

### Alternatives considered

- **Publish after commit with `@TransactionalEventListener`.** Fixes ghost events but not lost events. It
  would need a published-marker column and a reconciliation job to recover missed events, and that job can
  only rebuild events from the current row, losing intermediate versions, the original `user` and the
  original `occurredAt`. This is a weaker version of the outbox.
- **Change data capture (for example Debezium) on the outbox table.** Lower latency and no polling, but adds
  Kafka Connect infrastructure. A reasonable later upgrade that does not change the event contract.
- **Writing the audit directly in the relay or the service.** Simpler, but removes the decoupling and the
  partition-based scaling that were the goals of this work.

Direct publishing remains the right choice where losing an event is acceptable or there is no database
write to keep consistent (metrics, view events). That is not the case for the audit trail.

## Consequences

### Tradeoffs we accepted

- **Eventual consistency.** The audit row appears after the commit, typically within about a second
  (relay poll interval 500 ms plus consume time). A read immediately after a write may not show its audit.
  Nothing currently reads audits through the controller.
- **At-least-once delivery.** A crash after a Kafka send but before the outbox row is marked published
  re-sends the event. The consumer must be idempotent. It is, through the unique `event_id`.
- **Extra moving parts.** One table, one scheduled relay, one more failure point to monitor.
- **The relay holds a database transaction open while it sends** a batch (default 100, 10 s timeout per
  send). Acceptable at current volume. Revisit at high volume.
- **Parallelism is capped by partition count** (12). At most 12 consumer threads do work at once across all
  instances. Partitions can be increased but never decreased, and increasing them changes the key to
  partition mapping.

### Known gaps

These are deliberate omissions or open items. Do not assume they are bugs, and do not assume they are solved.

1. **Out-of-order events across relay instances.** `id` order is not commit order, and with several relays
   `SKIP LOCKED` gives different instances different batches. Events for one report can occasionally be
   published out of order (for example update 2, update 1, creation). Mitigation in place: every event and
   audit row carries `report_version` and `occurredAt`. Audit content is correct regardless of arrival order.
   **Open:** `findByTradeReportIdOrderReportVersionAsc` has no `ORDER BY`, so history may come back in arrival order. It should
   order by `report_version`. Any future consumer that applies state (cache, read model) must compare
   `version` and ignore stale events. If strict order is required, assign each report to one relay by
   `hash(report_id) % N`, or run a single relay.
2. **No automatic DLT replay.** Messages that exhaust retries (retry window about 10 s) are parked on
   `trade-report-events.DLT` and nothing reads that topic. A database outage longer than the retry window
   leaves audits missing until someone replays them. There is no alert on DLT size. **Planned:** a scheduled
   reconciliation job with a domain-specific minimum age, a maximum attempt count, and a parking-lot state
   for events that keep failing.
3. **Missing-audit detection is manual.** Outbox rows are marked published, never deleted, so they are a
   permanent record of what should have been audited. This finds gaps:
   ```sql
   select o.event_id, o.report_id, o.event_type, o.occurred_at
   from outbox_event o
   left join trade_reports_audits a on a.event_id = o.event_id
   where a.id is null and o.published_at < now() - interval '5 minutes';
   ```
   Replaying is safe: clear `published_at` on those rows and the relay re-publishes them. The unique
   `event_id` absorbs duplicates.
4. **No outbox retention policy.** The outbox table grows without bound. Add a cleanup rule (for example
   delete rows older than N days whose audits exist).
5. **Old messages fail.** Messages published before `eventId` existed are rejected by the event's null
   checks and go to the DLT.
6. **`cancelReport` is incomplete** (pre-existing). It saves nothing and is not exposed by the controller.
7. **Kafka is single-broker locally** (`replicas=1`). Use 3 replicas in any shared environment.

## Things that look wrong but are deliberate

- **The service does not write audits.** Do not add synchronous audit inserts back. That produces two audit
  rows per change.
- **The consumer is not transactional end to end with the offset.** It relies on idempotency (unique
  `event_id`) instead, which is why duplicates are swallowed rather than failed.
- **The consumer rethrows a constraint violation unless the `event_id` already exists.** A missing report
  (foreign key failure) is a real error and must reach the DLT, not be treated as a duplicate.
- **The relay query uses `FOR UPDATE SKIP LOCKED` even though only one relay instance runs today.**
  `OutboxEventRepository.claimBatch` is `SELECT ... WHERE published_at IS NULL ORDER BY id LIMIT :limit
  FOR UPDATE SKIP LOCKED`. `FOR UPDATE` locks the claimed rows until the relay's transaction commits, and
  `SKIP LOCKED` makes other instances skip those rows instead of waiting or sending them twice.
  - With one instance it has no visible effect. It is insurance for horizontal scaling and is **not**
    needed for correctness today. Do not remove it, and do not read it as a sign that several relays are
    already running.
  - Tradeoff: another instance gets the next *unlocked* rows, not necessarily the next ids, so events for
    one report can be published out of order (known gap 1). The audit history is ordered by
    `report_version` for that reason.
  - The locks last for the whole batch, because the relay holds one transaction open while it sends
    (see "Tradeoffs we accepted").
- **Audit immutability is enforced by a database trigger, not only by `@Immutable`.** `@Immutable` only
  affects Hibernate and does not stop raw SQL or deletes.
- **Audit time is the event's `occurredAt`,** not the time the consumer ran.
- **Credentials in `application.properties` and `docker-compose.yml` are local-development defaults.**
  Production must supply `DB_PASSWORD` and the other variables from a secrets store, with no fallback.

## References

- [README](../../README.md) for how to run and operate the stack
- `OutboxRelay`, `TradeReportConsumer`, `TradeReportAuditService`, `KafkaConsumerConfig`
- Migration `V1__baseline_and_audit_outbox.sql`
