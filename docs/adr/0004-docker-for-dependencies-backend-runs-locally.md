# ADR 0004: Docker for dependencies, backend runs locally

- **Status:** Accepted
- **Date:** 2026-11-24
- **Branch:** `feat-6-global-exception-handling`

## Context

The service needs Postgres and Kafka to run, and an integration test needs a real Postgres. Installing and
configuring both on a developer machine costs time that does not go into the application logic. It also
makes the setup differ from one machine to the next. A web dashboard (the sibling `TradeReportClient`
folder) also needs to be served somewhere.

## Decision

Run the **dependencies** in Docker and keep the **Spring Boot service** a normal local process.

- `docker-compose.yml` starts `postgres:16-alpine` and `apache/kafka:3.9.0` (KRaft, no ZooKeeper), each
  with a healthcheck. Optional web UIs (Kafka UI, pgAdmin) start only with `--profile ui`. Data lives in
  named volumes.
- The service runs from the IDE or `./mvnw spring-boot:run`. Its connection settings come from environment
  variables with local defaults (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP`), so the same build
  can point at other hosts without a code change.
- Integration tests use Testcontainers to start a throwaway Postgres (see ADR 0002), so tests never depend
  on whatever is in the compose database.
- The Angular dashboard is built and served from an nginx container (multi-stage `Dockerfile`: Node builds,
  nginx serves the static files). It reaches the backend directly from the browser, allowed by CORS.

All images are existing official ones. Nothing is built from scratch except the dashboard image.

## Why this split

- **One command, same versions.** `docker compose up -d postgres kafka` gives every machine the same
  database and broker. Nothing is installed locally, and `down -v` resets everything.
- **Focus on the logic.** Setting up Kafka and Postgres is not what the project is about.
- **Fast feedback on the service.** Running it from the IDE gives quick restarts and normal debugging.
  Rebuilding an image for every change would slow that down.
- **Only the dependencies are isolated.** The service itself is not, and this document should not say it is.

## Alternatives considered

- **Install Postgres and Kafka locally.** Works, but is slow to set up, differs between machines, and is
  hard to reset. Rejected.
- **Managed cloud services (hosted Postgres or Kafka) for development.** Adds cost, network dependence and
  accounts for no benefit at this stage. Rejected.
- **Embedded or in-memory substitutes (H2, embedded Kafka) as the main environment.** Fast, but they differ
  from the real engines. H2 cannot check the migration-only constraints or the audit trigger. Kept only for
  fast unit-style tests (ADR 0002).
- **Containerize the backend too and start everything from one compose file.** The natural next step, but
  it slows the edit and debug loop and adds Kafka networking details (see below). Deferred.
- **Kubernetes or another orchestrator.** Far more than a single-developer project needs. Rejected.

## Consequences

### Tradeoffs we accepted

- **Docker must be running** to develop or run the full test suite.
- **The backend is not containerized,** so it depends on the JDK on the machine (Java 21 target). "Works on
  my machine" is still possible for the Java side.
- **Starting the whole stack takes more than one command:** compose for the dependencies, then the backend.
- **Kafka has two addresses.** From the host it is `localhost:9092`. From another container it is
  `kafka:9094`. Anything that moves into a container must use the second one.
- **The dashboard container does not live-reload.** Each change needs a rebuild. Day-to-day work on the
  dashboard uses `npm start`.
- **The dashboard's backend address is fixed at build time** (`config.ts`), and the backend must list the
  dashboard's origin in `CORS_ALLOWED_ORIGINS`.

### Known gaps

1. **No backend Dockerfile.** Planned: a multi-stage build (Maven with a JDK, then a slim JRE image) and a
   `backend` service in compose that waits for Postgres and Kafka to be healthy and uses `kafka:9094`.
2. **Local credentials.** `tradereport`/`tradereport` and the pgAdmin login are development defaults. A
   shared environment must inject real secrets.
3. **Single-node Kafka** (`replicas=1`), as noted in ADR 0001.

## Things that look wrong but are deliberate

- **The service is not in `docker-compose.yml`.** It runs locally on purpose, for the debugging loop.
- **The dashboard has its own compose file** in its own folder, so it can be started and rebuilt without
  touching the database and broker stack.
- **Passwords appear in compose and `application.properties`.** They are local defaults, not secrets.

## References

- [ADR 0001](0001-transactional-outbox-for-trade-report-events.md) for the outbox and Kafka design
- [ADR 0002](0002-testcontainers-postgres-for-integration-tests.md) for Testcontainers
- `docker-compose.yml`, `README.md` (how to run), and `TradeReportClient/Dockerfile`
