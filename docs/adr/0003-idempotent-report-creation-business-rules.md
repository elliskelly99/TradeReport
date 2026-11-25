# ADR 0003: Business rules for idempotent report creation

- **Status:** Proposed (rules agreed in discussion; the implementation column below shows what is built)
- **Date:** 2026-11-24
- **Branch:** `feat-6-global-exception-handling`

## Context

Clients can retry a create request when a response is lost. Without a rule, a retry makes a second report,
a second outbox event and a second audit row. These rules are written down so the code, the tests and the
people reading them work from the same assumptions.

## The rules

Each rule is a plain sentence so it can become a test name.

| # | Rule | Built? |
|---|---|---|
| 1 | A report is identified by `(firm, tradeRefId)`. It is the key a client uses to say "this exact report". | Yes. Unique constraint `uq_trade_report_firm_ref` (V2). |
| 2 | Creating twice with the same key and the same data returns the existing report and creates nothing new. | **No.** `createNewReport` inserts blindly, so a retry gets a 409 today. |
| 3 | Creating with the same key but different data (for example another amount) is rejected with a 409. It is a client mistake, not a retry. | Partly. The constraint produces a 409, but it cannot tell a retry from a mistake. |
| 4 | `firm` and `tradeRefId` never change after creation. A wrong value is fixed by cancelling and re-reporting. | Yes. Neither field has a setter, both columns are `updatable = false`, and the update request (`UpdateAmount`) has no `firm`. |
| 5 | An update may change the amount and the status. | Yes. |
| 6 | Every change produces an audit row, and audit rows are immutable. | Yes. Outbox, consumer and trigger (ADR 0001). |
| 7 | A bulk create is all-or-nothing. A repeated key inside one request is a 400. A key that already exists makes the whole request a 409. | Yes. |

## Decisions and reasoning

- **The identity must not change.** If the key can change, a client retrying the original create finds
  nothing and a duplicate is created. The audit cannot prevent this: it records history after the fact and is
  not read when a create arrives. This is why `tradeRefId` has no setter, and why `firm`, which is half of the
  key, should not be updatable either.
- **The database is the final authority.** The application checks for duplicates early and returns a clear
  error, but the unique constraint is what holds when two requests arrive at the same moment.
- **Retry versus mistake is decided by comparing data.** Same key and same data is a retry (rule 2). Same key
  and different data is a conflict (rule 3).

## Deliberately not done

- **No normalization of `firm` or `tradeRefId`.** Values are compared exactly as sent. `FirmA` and `firma` are
  different firms, and `REF-1` and `REF-1 ` are different references. Trimming and case-insensitive matching
  were tried and reverted as over-engineering for this stage. If the business later decides they are the same,
  the fix is a case-insensitive unique index in a new migration, or a firms reference table, and not
  lowercasing in code.
- **No renames through an audited correction.** Cancel and re-report is the supported way to fix a wrong key.

## Open items

1. **Build rules 2 and 3.** Look up `(firm, tradeRefId)` before inserting. Equal data returns the existing
   report with a 200. Different data returns a 409. Handle two requests racing by re-reading in a new
   transaction after a constraint failure.
2. ~~Remove `firm` from the update request~~ **Done.** `UpdateAmount` has no `firm`, the setter is gone, and
   `firm` and `tradeRefId` are `updatable = false`. A client that still sends `firm` has it ignored.
3. ~~Carry `tradeRefId` in the event and the update response~~ **Done.** The event, outbox rows, audit rows
   (V3) and `UpdateResponse` all carry it.
4. **Decide the bulk retry behavior.** A retried bulk request currently fails entirely with a 409. It could
   instead return the existing reports when every item matches.
5. **Optional:** a database trigger that rejects changes to `firm` and `trade_ref_id`, like the one on the
   audit table, to block raw SQL.

## References

- [ADR 0001](0001-transactional-outbox-for-trade-report-events.md) for the outbox, consumer and audit design
- [ADR 0002](0002-testcontainers-postgres-for-integration-tests.md) for how the constraint is tested against a real Postgres
- `TradeReportService.createNewReport`, `TradeReportService.createNewReportBulk`, `GlobalExceptionHandler`
- Migration `V2__alter_trade_report_table.sql`
