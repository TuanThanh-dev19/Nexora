# UI MVP coverage / acceptance — issue #62

Branch: `feature/56-ui-navigation-quality`, based on updated `develop`.
This is an integration checkpoint, **not final MVP acceptance**. Pending entry
pages are explicit placeholders, not substitutes for the dependent feature UIs.
Do not close #62 or merge this checkpoint as a completed MVP until its remaining
dependencies and visual QA below have been verified.

## Coverage snapshot (11 October 2026)

REAL below describes the client/API implementation available in source, not proof
that its full database journey was exercised in this audit. MOCK means in-memory,
development/test data; PENDING means missing feature implementation/integration.

| Screen / journey | UI and API status | Dependency / remaining acceptance |
| --- | --- | --- |
| Login, restore, logout, 401 | REAL auth clients; automated session/guard tests | Real-session browser QA still required; transient network restore now retains route/token and offers retry |
| Users list/detail/create/status/roles | Existing UI; REAL backend endpoints; optional DEV adapter | Follow-up #51, current role/error/form/browser acceptance still required |
| Technician list/profile/skills/service areas | PENDING UI (#52); profile backend present, list contract pending | `/technicians`, `/technicians/:technicianId`, own read-only `/technicians/me` are guarded pending entries; integrate owner's UI |
| Location tree/admin and equipment catalog | PENDING UI (#54); REAL APIs present | Admin-only pending entries `/administration/locations`, `/administration/equipment-types`; no fake CRUD |
| Asset list/create/edit/detail/status/history | PENDING UI (#55); REAL APIs present | `/assets` pending entry; owner's UI must replace it before final acceptance |
| Category/form admin | Existing REAL clients/editor; completion PENDING #53 | Controls/ordering/Vietnamese labels/dirty/conflict UI belong to owner's completion; no duplicate editor built here |
| Ticket list/detail/history/metadata | REAL read clients and APIs; component/MSW tests | Real role/session/database browser QA pending |
| Create Ticket + dynamic fields + evidence photos | PENDING UI #57; create/attachment API future | REQUESTER-only `/tickets/new` pending entry; no production mock submission |
| Manager review/assignment | MOCK only with `VITE_TICKET_WORKFLOW_MOCK=true` in DEV; runtime mutation unavailable | #58 UI exists; real workflow/eligibility backend acceptance separate |
| Technician My Work/log/resolve | MOCK workflow preview; real writes pending | #59 UI exists; TECHNICIAN-only `/my-work`; real concurrency/persistence not proven |
| Attachment content / comments / feedback | Partial MOCK workflow upload; remainder PENDING #60/backend | Do not claim real image download/comments/feedback coverage |
| Dashboard | Original contract/shell #41; completion #61 in PR #67, not merged at snapshot | `/dev/dashboard` in #61 only; real query/controller pending; don't report fake metrics as production data |
| Navigation and guards | Automated role/mixed-role tests, preserved 403/404 route context | Protected routes stay authenticated; navigation is the union of roles, not backend authorization |
| Responsive / keyboard / photos | Keyboard menu/accessible guards automated; visual QA PENDING | Check desktop 1440, tablet 768, mobile 390; photo form coverage requires #57/#60 |

The dependency `#42` written in #62 currently resolves to an already merged Ticket
persistence PR, not the new Login/User UI issue. Relevant auth/UI follow-up is #51.
This document does not renumber or alter team issues.

## What this checkpoint changes

- Central role-union navigation. REQUESTER alone gets Create Ticket; TECHNICIAN
  gets My Work and own-profile; MANAGER/ADMIN get workforce/assets; ADMIN gets
  Users, Locations/Equipment and Categories. A multi-role user gets their union.
- Management dropdown retains the existing AppLayout/palette/Bootstrap design.
  Protected links are hidden during session checking and for anonymous visitors.
- Missing routes show a real 404 without redirecting home. Role denial shows the
  existing 403 page in place; route render errors offer retry without changing URL.
- Logout/account change/401 cancel and clear **all** private query caches,
  including administration and Dashboard, not just Tickets.
- A transient `/auth/me` network/5xx failure no longer deletes the session or
  redirects away from the intended page; retry restores it. 401/403 still revoke it.
- Shared timestamp formatting is vi-VN / explicit Asia/Bangkok, independent of
  device timezone. Existing date-only Ticket formatting remains UTC to avoid
  changing the date. Existing feature state/badge/form components are retained.

## Run / distinguish preview from production

In `frontend`, install using `npm.cmd ci`, then run `npm.cmd run dev`. Start the
real backend separately for login/read/admin APIs. Use existing ignored local
configuration; never commit account passwords/tokens or replace production APIs
with mock success responses.

For ticket workflow demonstration only:

```powershell
$env:VITE_TICKET_WORKFLOW_MOCK = 'true'
npm.cmd run dev
# Stop Vite, then:
Remove-Item Env:VITE_TICKET_WORKFLOW_MOCK
```

See `docs/TESTING.md` for fixture IDs/scenarios and limitations. Authentication
remains real. After #61 merges, enable `VITE_DASHBOARD_MOCK=true` and open
`/dev/dashboard`; see `docs/DASHBOARD_UI.md`. Do not enable two separate MSW
workers: #61 integrates dashboard/ticket handlers in one worker. Mock flags must
be gated by DEV; a production build must not contain browser mock modules or
preview controls even when flags are set. User admin's existing DEV adapter is
not proof of database persistence or server authorization.

## Pre-merge checklist (remaining)

- [ ] Integrate actual #52/#54/#55 feature screens, #53 completion and #57 create
  flow. Replace pending entries with owner's components; preserve guards.
- [ ] Merge verified #61 and reconcile router imports/preview route.
- [ ] Check all four roles and a mixed-role session: restore, logout, 401, 403,
  404, active nav, back links, form validation, retry, stale/conflict and empty.
- [ ] Browser screenshots/review at 1440/768/390px: no horizontal overflow;
  card/table/menu layout, keyboard Tab/Enter/Escape, visible focus, photo preview.
- [ ] Confirm real available APIs with real accounts/data. Separately record
  pending backend journeys; UI mock acceptance does not claim real integration.
- [ ] Final full frontend lint/test/build and mock-enabled production build guard.
- [ ] Compare exact branch name and all original #62 acceptance criteria before
  marking it done. Do not merge/close based solely on this partial checkpoint.

Local computer-use was unavailable (`native pipe ... system cannot find the file`
and no connected browser), including after one reset/retry. This prevents visual
acceptance in this session; it is not evidence that the UI works on those sizes.
