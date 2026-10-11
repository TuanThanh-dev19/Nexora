# Dashboard shell — #41 / #61

Branches: initial `feature/33-dashboard-contract-shell`, follow-up
`feature/55-dashboard-shell-ui`. This is a contract/UI delivery, not a real
dashboard query or controller. Production calls GET `/api/v1/dashboard/summary`
with the existing Bearer session and shows an unavailable state while the API is
pending. It never substitutes preview numbers. Metric names remain:

| Field | Definition |
| --- | --- |
| openTickets | Nonterminal tickets excluding RESOLVED |
| inProgressTickets | IN_PROGRESS tickets |
| slaAtRiskTickets | Active tickets overdue or due within the next two hours |
| resolvedThisWeek | resolvedAt in the current ISO week in Asia/Bangkok |

Requester scope is own tickets; Technician is current/historical assignments;
Manager/Admin is all tickets. Mixed roles use the wider applicable scope copy.
This is presentation only: future backend authorization must independently enforce
the scope. Values are nonnegative integers, generatedAt is an ISO timestamp.
The UI always formats it with vi-VN and explicit Asia/Bangkok.

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
