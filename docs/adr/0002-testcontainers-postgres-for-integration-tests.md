# ADR 0002: Real Postgres (Testcontainers) for database integration tests

- **Status:** Accepted
- **Date:** 2026-11-23
- **Branch:** decided on `feat-4-testcontainers`

## Context

The first tests ran against in-memory H2 with `ddl-auto=create-drop` and Flyway turned off. That database
builds its schema from the entity annotations, so it never sees the Flyway migrations. Several rules this
service depends on exist **only** in the migrations:

- `uq_trade_report_firm_ref`, the unique `(firm, trade_ref_id)` constraint that makes report creation
  idempotent (V2).
- `uq_audit_event_id`, the unique `event_id` that makes the audit consumer idempotent (V1).
- The `trg_audit_immutable` trigger that rejects `UPDATE` and `DELETE` on `trade_reports_audits` (V1).

With H2, a test for any of these either passes for the wrong reason or cannot be written. Postgres-specific
behavior (types, trigger functions, error codes) is also not reproduced faithfully by H2.

## Decision

Run database integration tests against a **real Postgres 16 started by Testcontainers**, using the same Flyway
migrations as production.

- `PostgresTestConfig` (`@TestConfiguration`) declares one `PostgreSQLContainer("postgres:16-alpine")`, the
  same image as `docker-compose.yml`. `@ServiceConnection` points the datasource at it, so no URL or password
  is configured by hand.
- A test opts in with `@SpringBootTest`, `@Import(PostgresTestConfig.class)` and `@ActiveProfiles("pg")`.
- `application-pg.properties` turns Flyway **on** and sets `ddl-auto=validate`. Flyway builds the schema, and
  Hibernate fails startup if an entity and the schema ever drift apart.
- Spring caches the application context, so every test class with the same setup shares one container for the
  whole run. The container is removed when the test JVM exits.

Tests live under `src/test/java/.../integration/`. Fast tests (Mockito unit tests, slice tests) stay where
they are and keep using H2 or no database.

## Scope: Kafka is out of the Postgres container tests

These tests cover the **database only**. There is no Kafka broker, and none is started.

- The listener and the relay are off in test config (`spring.kafka.listener.auto-startup=false`,
  `app.outbox.relay.enabled=false`).
- Topic auto-creation is off (`spring.kafka.admin.auto-create=false`). Without it, the app waited about 40
  seconds at startup for a broker that was never there.
- Consumer logic is tested by calling `TradeReportConsumer.consume(...)` directly. The outbox write is checked
  by reading the `outbox_event` table.

What this means: nothing here proves the full pipeline (outbox row, relay, topic, consumer, audit row) works,
and nothing proves the DLT receives a bad message. That was verified by hand with Kafka UI and is not
automated yet.

## Alternatives considered

- **Keep H2.** Fast and needs nothing installed, but it cannot test migration-only constraints or the
  trigger. Rejected for the rules above.
- **Point tests at the docker-compose Postgres.** Real database, but tests would depend on whatever data is
  already in it, on someone starting it first, and would pollute the dev database. Rejected.
- **H2 in PostgreSQL compatibility mode.** Closer to Postgres, but still no triggers written in PL/pgSQL and
  still a different engine. Rejected.
- **Reusable (long-lived) Testcontainers.** Faster reruns, but leaves data between runs and removes the clean
  slate. Not enabled.
- **A Kafka container in the same setup.** Deliberately left out of this change; see below.

## Consequences

### Tradeoffs we accepted

- **Docker must be running** to run the Postgres tests. CI needs Docker too.
- **Startup cost.** The first Postgres test class takes roughly 8 to 20 seconds to start the container and run
  Flyway. Later classes reuse it.
- **Tests commit for real.** The Postgres tests are not `@Transactional`. Each repository or service call
  commits, as in production. This is needed because a failed insert inside one open transaction leaves
  Postgres in an aborted state, and because `recordAudit` has its own transaction boundary that should be
  exercised. The cost is that rows accumulate during a run.
  - Each test must therefore use its own data, for example a random firm name (`firm-<uuid>`).
  - Do not assert on whole-table counts such as `count() == 0`.
  - The audit table cannot be cleaned with `DELETE` (the trigger blocks it). Use `TRUNCATE ... CASCADE` if a
    reset is ever needed.
- **Context sharing is fragile.** A different profile, import, property or mock bean in one test class gives
  it its own application context and its own container, which slows the suite. Keep the setup annotations on
  integration tests identical.

### Conventions

- Test classes end in `Test` (for example `TradeReportPostgresTest`). Maven Surefire does not run classes
  ending in `IT`, and a first version named `...IT` was silently skipped by `./mvnw test`.
- `PostgresTestConfig` is in the same `integration` package. Move it to a shared `support` package only when
  a second package needs it.

### Known gaps

1. **No automated end-to-end Kafka test.** Planned: a small number of tests with a Kafka Testcontainer that
   cover outbox to topic to audit row, a bad message reaching the DLT, and a duplicate delivery producing one
   audit row. Keep these in a separate class so the rest of the suite stays fast.
2. **No per-test cleanup.** Safe today because tests use unique data. Add a reset if a test needs a known
   empty state.
3. **Unit and slice tests still use H2** (`src/test/resources/application.properties`). They do not run the
   migrations, so they must not be used to check constraints.

## Things that look wrong but are deliberate

- **`spring.kafka.admin.auto-create=false` in test config.** Not a missing feature. There is no broker in
  these tests.
- **The Postgres tests are not `@Transactional`.** Adding it would hide the constraint failures they exist
  to check.
- **`application-pg.properties` leaves out the datasource URL and password.** `@ServiceConnection` supplies
  them from the container at runtime.
- **Random firm names in tests.** They keep committed rows from colliding across tests.

## References

- [ADR 0001](0001-transactional-outbox-for-trade-report-events.md) for the outbox, consumer and audit design
- `PostgresTestConfig`, `TradeReportPostgresTest`, `application-pg.properties`
- Migrations `V1__baseline_and_audit_outbox.sql` and `V2__alter_trade_report_table.sql`
