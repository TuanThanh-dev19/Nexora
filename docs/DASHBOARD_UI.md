# Dashboard — #41 / #61 / #72

Branches: initial `feature/33-dashboard-contract-shell`, follow-up
`feature/55-dashboard-shell-ui`; real API: `feature/dashboard-summary-api`.
Production calls GET `/api/v1/dashboard/summary` with the existing Bearer session.
The backend reloads current database roles and runs one SQL aggregate through the
existing EXISTS-based Ticket visibility specification. It never substitutes
preview numbers or converts a database failure into zeros. Metric names remain:

| Field | Definition |
| --- | --- |
| openTickets | Nonterminal tickets excluding RESOLVED |
| inProgressTickets | IN_PROGRESS tickets |
| slaAtRiskTickets | Active tickets overdue or due within the next two hours |
| resolvedThisWeek | resolvedAt in the current ISO week in Asia/Bangkok |

Requester scope is own tickets; Technician is current/historical assignments;
Manager/Admin is all tickets. Mixed roles use the union of those scopes, with each
Ticket counted only once. This is enforced in the backend query, not by UI hiding.
Values are nonnegative integers, generatedAt is an ISO timestamp.
The UI always formats it with vi-VN and explicit Asia/Bangkok.

## Real API (#72)

No query parameters are accepted (400). Missing/invalid identity returns 401;
no supported role returns 403. Database failures produce safe 500 JSON problems.
One Clock instant anchors generatedAt (+07:00), SLA <= now + 2 hours (including
overdue), and the half-open ISO week [Monday 00:00, next Monday 00:00) in Bangkok.
Open statuses are SUBMITTED/UNDER_REVIEW/ASSIGNED/IN_PROGRESS/REOPENED; resolved
counts use resolvedAt, including CLOSED, never updatedAt/closedAt. No visible
Tickets returns four zeros with a real timestamp. No entities are loaded to count.

Create Ticket already invalidates Dashboard; manager and technician mutation hooks
now do so too. Returning to Dashboard also refetches stale data. User ID/roles are
in its cache key and auth retains the existing private-cache logout cleanup.
This does not enable unfinished workflow APIs or production mutation mocks.

Tests use a fixed Bangkok instant with a Los Angeles Clock zone; the same query
contract runs on H2 and SQL Server (RUN_SQLSERVER_IT=true in CI). Fixture status
updates prove read-query behavior, not a completed real Create/Start/Resolve/Close
journey. Live mutation acceptance remains separate and depends on those APIs.

## Development preview

From `frontend`:

```powershell
$env:VITE_DASHBOARD_MOCK = 'true'
npm.cmd run dev
# After stopping Vite:
Remove-Item Env:VITE_DASHBOARD_MOCK
```

Open `/dev/dashboard` (development only). The warning identifies fictional,
nonpersistent data. Select REQUESTER/TECHNICIAN/MANAGER/ADMIN or mixed roles and
success/zero/loading/error/retry. For retry, the first response fails; clicking
Thử lại succeeds. Automatic retries are disabled for this pending contract.
Mock auth is not installed: role selection affects only this preview component,
not the application's actual session or permissions. The regular dashboard still
requires login. If ticket workflow mocks are enabled too, one shared worker serves
both contracts; unrelated API requests pass through unchanged.

Production excludes the preview route and lazy mock modules regardless of flags.
Run `npm.cmd run lint`, `npm.cmd test`, `npm.cmd run build`; timestamp tests may
also run with `$env:TZ='America/Los_Angeles'` to confirm the device does not change
the displayed Bangkok time. Check 1440/768/390px layouts: four/two/one cards,
readable labels, no horizontal overflow, keyboard focus and retry. UI/MSW tests
do not prove real SQL metrics, backend authorization or workflow persistence.
