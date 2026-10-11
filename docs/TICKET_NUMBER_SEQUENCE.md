# Ticket number sequence — issue #39

Branch: `feature/31-ticket-number-sequence`. Migration: V10 (V9 already belongs
to category concurrency). V1–V9 are unchanged.

`TicketNumberGenerator.nextNumber(createdAt)` uses JDBC `NEXT VALUE FOR
dbo.ticket_number_sequence`. The future create-ticket transaction must pass the
same creation instant it persists. The calendar year is converted to Asia/Bangkok;
for example `2025-12-31T17:00:00Z` produces year 2026.

The BIGINT counter is global, never resets at New Year and never cycles. Six
digits is minimum padding, not truncation: 123 becomes `NX-2026-000123`, while
10 million becomes `NX-2026-10000000`. Even BIGINT maximum fits VARCHAR(30).
Rollback and sequence caching/server restart may leave gaps; this is expected.
The existing `uq_tickets_ticket_number` remains the final database uniqueness
constraint. Do not restart the sequence on a populated database, use MAX(id)+1,
or allocate ten million numbers in a single HTTP request. This component does
not implement ticket creation, batch creation, rate limits, or idempotency.

Verify using an isolated SQL Server Testcontainers database (Docker required):

```powershell
cd backend
$env:RUN_SQLSERVER_IT = 'true'
.\mvnw.cmd "-Dtest=TicketNumberGeneratorTest,TicketNumberSequenceSqlServerIntegrationTest,SqlServerMigrationIntegrationTest" test
```

Tests cover fresh migrations, the existing unique index, consecutive allocations,
year/padding boundaries, values beyond six digits, rollback gaps and 1,000 JDBC
allocations through 16 threads. They do not load-test ten million real tickets or
claim that future ticket creation/transaction code is implemented.
