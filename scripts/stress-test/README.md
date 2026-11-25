# Stress test: duplicate creates

Sends the **same** `POST /createNewReport` request 1000 times, 50 at a time, and checks that the service ends
up with exactly **one** report for that firm and trade reference.

It is separate from the application. It lives outside `src/`, is not part of the Maven build or the unit tests,
needs only Python 3 (standard library, no packages), and talks to the running backend over HTTP only.

## Run it

Start the backend first (`./mvnw spring-boot:run`), then:

```bash
python3 scripts/stress-test/stress_create.py
```

Each run uses a new trade reference (`STRESS-<timestamp>`), so it starts from a clean slate.

| Option | Default | Meaning |
|---|---|---|
| `--url` | `http://localhost:8080` | Backend base URL |
| `--requests` | `1000` | How many requests to send |
| `--workers` | `50` | How many to send at the same time |
| `--ref` | `STRESS-<timestamp>` | Trade reference every request uses (reusing one makes every request fail from the start) |
| `--firm` | `STRESS TEST FIRM` | Firm every request uses |
| `--no-verify` | off | Skip checking what the service stored |
| `--allow-remote` | off | Allow a target that is not on this computer |

Safety guard: the script refuses any target that is not `localhost`, `127.0.0.1`, `::1` or
`host.docker.internal`, unless you pass `--allow-remote`. Only use that on a system you own.

## What a run looks like

```
sending 1000 identical creates to http://localhost:8080/createNewReport, 50 at a time
firm='STRESS TEST FIRM'  tradeRefId='STRESS-1795635083'

total time:   1.45s  (688 requests/second)
status codes:
  200:     1   created
  409:   999   already exists (early check, or the unique constraint in a real race)
latency ms:   min 11   p50 59   p95 109   p99 136   max 183

verification (asking the service what it stored)
  reports with this firm and reference: 1   (expected 1)
  audit rows for report 11082: 1   (expected 1)

RESULT: PASS
```

The 409 count is 999 on a clean run. The one thing that must not change is **one report**.

## What the status codes mean

| Code | Meaning |
|---|---|
| 200 | The winner. It created the report. |
| 409 | The report already exists. Most requests are refused by the early check (`existsBy...`). A few may pass it at the same moment as the winner, and the database's unique constraint (`uq_trade_report_firm_ref`) blocks their insert. Both give the same 409 (see ADR 0003). |

## Pass and fail

The script exits with code `0` (PASS) when both are true:
- no server errors (5xx) and no dropped connections, and
- exactly one report exists for the firm and reference afterwards.

It exits with `1` on a failure, and with `2` if it never started (backend not running, or a remote target
without `--allow-remote`). The audit row is only a warning, because the Kafka consumer writes it
asynchronously and might not be running.

The check reads `GET /getReports` (filtered by the test's firm and reference) and `GET /getReportAudits/{id}`, so the stress reports must be
among the newest 200.

## Cleanup

Each run leaves one report, one outbox event and one audit row under the firm `STRESS TEST FIRM`. Audit rows
cannot be deleted (a database trigger rejects it), and they reference the report, so the clean way to remove
them is to reset the local database:

```bash
docker compose --profile ui down -v    # deletes ALL local data, then start the stack again
```

## Why it exists

It shows the unique constraint protecting the data under real concurrency: an early check catches nearly every
duplicate, and the constraint catches the few that slip through at the same instant. Both answer 409.
