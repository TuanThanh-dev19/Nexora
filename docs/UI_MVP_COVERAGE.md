# UI MVP coverage / acceptance — issue #62

Branch: `feature/56-ui-navigation-quality`, based on updated `develop`.
This delivers the shared navigation/session fixes and integrates the verified
Dashboard UI from PR #67. **Full MVP acceptance still depends on the other
owners' feature screens.** Pending entry pages are explicit placeholders, not
substitutes for those UIs. Do not close #62 or describe this PR as a completed
MVP until those dependencies have been integrated and checked.

## Coverage snapshot (11 October 2026)

REAL below describes the client/API implementation available in source, not proof
that its full database journey was exercised in this audit. MOCK means in-memory,
development/test data; PENDING means missing feature implementation/integration.

| Screen / journey | UI and API status | Dependency / remaining acceptance |
| --- | --- | --- |
| Login, restore, logout, 401 | REAL auth clients; real local login/restore/logout browser QA passed for four roles and a Requester+Technician account | Fault-injected network/503 retains route/token and retries real auth/me; injected 401/403 revokes session, not proof of real server expiry |
| Users list/detail/create/status/roles | Existing UI; REAL backend endpoints; optional DEV adapter | Real list/filter/empty/retry and local form validation checked; Cancel now resets the draft like header close. Real create/status/role mutations were not exercised; feature follow-up remains #51 |
| Technician list/profile/skills/service areas | PENDING UI (#52); profile backend present, list contract pending | `/technicians`, `/technicians/:technicianId`, own read-only `/technicians/me` are guarded pending entries; integrate owner's UI |
| Location tree/admin and equipment catalog | PENDING UI (#54); REAL APIs present | Admin-only pending entries `/administration/locations`, `/administration/equipment-types`; no fake CRUD |
| Asset list/create/edit/detail/status/history | PENDING UI (#55); REAL APIs present | `/assets` pending entry; owner's UI must replace it before final acceptance |
| Category/form admin | Existing REAL clients/editor; completion PENDING #53 | Controls/ordering/Vietnamese labels/dirty/conflict UI belong to owner's completion; no duplicate editor built here |
| Ticket list/detail/history/metadata | REAL read clients and APIs; component/MSW tests | Real role/session/database browser QA pending |
| Create Ticket + dynamic fields + evidence photos | PENDING UI #57; create/attachment API future | REQUESTER-only `/tickets/new` pending entry; no production mock submission |
| Manager review/assignment | MOCK only with `VITE_TICKET_WORKFLOW_MOCK=true` in DEV; runtime mutation unavailable | #58 UI exists; real workflow/eligibility backend acceptance separate |
| Technician My Work/log/resolve | MOCK workflow preview; real writes pending | #59 UI exists; TECHNICIAN-only `/my-work`; real concurrency/persistence not proven |
| Attachment content / comments / feedback | Partial MOCK workflow upload; remainder PENDING #60/backend | Do not claim real image download/comments/feedback coverage |
| Dashboard | Verified #61 UI/preview from PR #67 integrated into this branch | Four Vietnamese metrics, role scope, Bangkok time, loading/zero/success/error/retry and 4/2/1 layout checked; real summary still 404/pending, never fake success |
| Navigation and guards | Real four-role/mixed-role browser QA, direct paths, active links, preserved 403/404 and private cache reset | Navigation remains a role union. Existing admin/users API separately returned 200 only for Admin and 403 for the other test roles |
| Responsive / keyboard / photos | Chrome/Playwright visual QA at 1440/768/390; menu label/expanded state and keyboard fixes | Available shell, Users table/modal, Dashboard and local result-image preview checked; Create Ticket photo form still requires #57/#60 |

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
- Mobile navigation publishes its actual expanded state, uses the Vietnamese
  accessible label, and collapses for both private and anonymous navigation.
- The existing Create User Cancel action uses the same draft/error/password
  reset path as header close. This is a UI consistency correction, not a new
  user-management workflow or backend mutation.
- Dashboard integration retains the role guards and in-place route errors from
  this branch; `/dev/dashboard` remains public presentation only in DEV with the
  explicit mock flag. One worker serves both Dashboard and Ticket previews.

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
remains real. This branch includes #61: enable `VITE_DASHBOARD_MOCK=true` and open
`/dev/dashboard`; see `docs/DASHBOARD_UI.md`. Do not enable two separate MSW
workers: #61 integrates dashboard/ticket handlers in one worker. Mock flags must
be gated by DEV; a production build must not contain browser mock modules or
preview controls even when flags are set. User admin's existing DEV adapter is
not proof of database persistence or server authorization.

## Pre-merge checklist (remaining)

- [ ] Integrate actual #52/#54/#55 feature screens, #53 completion and #57 create
  flow. Replace pending entries with owner's components; preserve guards.
- [x] Integrate verified #61 and reconcile router imports/preview route.
- [x] Check available routes with four roles and a mixed-role session: restore,
  logout, injected 401/403, real role denial, 404, active nav, local validation,
  retry and empty. Additional owner-screen journeys remain pending above.
- [x] Review available screens at 1440/768/390px: no page horizontal overflow;
  table scroll stays internal; keyboard/focus and local result-image preview.
- [x] Check real login/auth/read APIs with local accounts; clearly separate MSW
  workflow/conflict success from real persistence and missing backend journeys.
- [x] Frontend lint and 295-test full regression passed; the final anonymous-menu
  change also passed all four AppLayout tests. Mock-enabled production build and
  real Chrome production login/summary/preview guard checks passed.
- [ ] Compare exact branch name and all original #62 acceptance criteria before
  marking it done. Do not merge/close based solely on this partial checkpoint.

The native computer-use bridge was unavailable (`native pipe ... system cannot
find the file`). With the user's authorization, visible Chrome was controlled
using Playwright instead. The browser QA above was actually exercised; the
native-bridge failure is no longer a visual-QA blocker. Images and raw results
are local QA artifacts outside the repository; no account secrets are committed.

Remaining ownership boundary: #52 is assigned to Huytran2903, #54/#55 to
OanhPhiTo and #53/#57 to thoaithanh-web. Their current feature/label completion
must be integrated when delivered. This PR does not implement those assigned
features or claim the existing Category English labels meet final consistency.
