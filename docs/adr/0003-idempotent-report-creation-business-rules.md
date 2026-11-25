# ADR 0003: Business rules for report creation (strict create)

- **Status:** Accepted (create is strict; the implementation column shows what is built)
- **Date:** 2026-11-24
- **Branch:** `feat-6-global-exception-handling`

## Context

Clients can retry a create request when a response is lost. Without a rule, a retry makes a second report,
a second outbox event and a second audit row. These rules are written down so the code, the tests and the
people reading them work from the same assumptions.

**The decision: create is strict.** If a report for the same `(firm, tradeRefId)` already exists, the create
fails with a 409, whatever data it carries. It does not return the existing report and it never changes
anything. If the client wants a different amount, it updates the existing report on purpose. A retry of a lost
create therefore ends in a clear "already exists" answer and never in a duplicate.

## The rules

Each rule is a plain sentence so it can become a test name.

| # | Rule | Built? |
|---|---|---|
| 1 | A report is identified by `(firm, tradeRefId)`. It is the key a client uses to say "this exact report". | Yes. Unique constraint `uq_trade_report_firm_ref` (V2). |
| 2 | Creating with a key that already exists is refused with a 409 (`ReportAlreadyExistsException`), whatever the amount. Nothing new is created: no report, no outbox event, no audit row. | Yes. `createNewReport` checks `existsByTradeRefIdAndFirm` first. |
| 3 | If two creates with the same new key arrive at the same moment, exactly one wins and the other also gets a 409. | Yes. The unique constraint stops the loser (`DataIntegrityViolationException`, mapped to 409). |
| 4 | `firm` and `tradeRefId` never change after creation. A wrong value is fixed by cancelling and re-reporting. | Yes. Neither field has a setter, both columns are `updatable = false`, and the update request (`UpdateAmount`) has no `firm`. |
| 5 | An update may change the amount and the status. | Yes. |
| 6 | Every change produces an audit row, and audit rows are immutable. | Yes. Outbox, consumer and trigger (ADR 0001). |
| 7 | A bulk create is all-or-nothing. A repeated key inside one request is a 400. A key that already exists makes the whole request a 409. | Yes. |
| 8 | An update must send the `version` the client last saw. A stale version is a 409 ("Concurrent update"). Updating to the amount the report already has is a 409. Nothing is overwritten silently. | Yes. |

## Decisions and reasoning

- **The identity must not change.** If the key can change, a client retrying the original create finds
  nothing and a duplicate is created. The audit cannot prevent this: it records history after the fact and is
  not read when a create arrives. This is why `tradeRefId` has no setter, and why `firm`, which is half of the
  key, should not be updatable either.
- **The database is the final authority.** The application checks for duplicates early and returns a clear
  error, but the unique constraint is what holds when two requests arrive at the same moment.
- **One answer for "already exists".** The early check and the constraint both end in a 409, so a client
  handles one case. The only difference is the wording of the message.
- **Why strict and not "return the existing report".** Returning the existing report hides the case where the
  second request carried different data, and for a compliance record the client must know that its create did
  not take effect. The client updates deliberately, with the version it saw (rule 8).
- **Tradeoff: a lost response still needs a follow-up.** After a retry gets a 409 the client does not know
  whether its own first request created the report. It reads the report and compares. The alternative
  (idempotency keys) would answer a retry with the original result, but adds a table and more code.

## Deliberately not done

- **No normalization of `firm` or `tradeRefId`.** Values are compared exactly as sent. `FirmA` and `firma` are
  different firms, and `REF-1` and `REF-1 ` are different references. Trimming and case-insensitive matching
  were tried and reverted as over-engineering for this stage. If the business later decides they are the same,
  the fix is a case-insensitive unique index in a new migration, or a firms reference table, and not
  lowercasing in code.
- **No renames through an audited correction.** Cancel and re-report is the supported way to fix a wrong key.

## Open items

1. ~~Build rules 2 and 3~~ **Done, as strict create.** The early check throws `ReportAlreadyExistsException`
   (409) and the constraint covers a race. Returning the existing report was considered and rejected, see
   Decisions.
2. ~~Remove `firm` from the update request~~ **Done.** `UpdateAmount` has no `firm`, the setter is gone, and
   `firm` and `tradeRefId` are `updatable = false`. A client that still sends `firm` has it ignored.
3. ~~Carry `tradeRefId` in the event and the update response~~ **Done.** The event, outbox rows, audit rows
   (V3) and `UpdateResponse` all carry it.
4. **Bulk retry behavior.** A retried bulk request fails entirely with a 409, which matches strict create.
   A key that already exists is found by the constraint, so the message is the generic "Conflict" and not
   "Report already exists".
5. **Optional:** a database trigger that rejects changes to `firm` and `trade_ref_id`, like the one on the
   audit table, to block raw SQL.

## References

- [ADR 0001](0001-transactional-outbox-for-trade-report-events.md) for the outbox, consumer and audit design
- [ADR 0002](0002-testcontainers-postgres-for-integration-tests.md) for how the constraint is tested against a real Postgres
- `TradeReportService.createNewReport`, `TradeReportService.createNewReportBulk`, `GlobalExceptionHandler`
- Migration `V2__alter_trade_report_table.sql`
