# Ticket reassignment implementation report

Issue: NEX-051 / #92. Branch: feat/ticket-reassignment. Baseline: a26f98c.
Date: 30 October 2026 (Asia/Bangkok).

## Implementation summary

Implemented POST /api/v1/tickets/{ticketId}/reassign using the existing assignment service,
strict action reader, eligibility checks, current database roles and SQL transaction.
Only ASSIGNED Tickets with an active assignment can be reassigned. The destination must differ
from the current technician. The required reason is trimmed and limited to 1000 characters.

The old assignment is ended and flushed before the new row is inserted, preserving SQL Server's
filtered unique index. Ticket.touch() makes the same-status change increment @Version once.
REASSIGN history records actor, reason and the same server instant as the assignment replacement.
The destination profile lock serializes capacity checks with normal assignment. Workload is derived
from active assignment rows, with no new counters.

Ticket Detail exposes assignmentHistory using the existing public assignment shape. The frontend
reuses the assignment dialog, disables the current/ineligible candidate, preserves pending/conflict
behavior, renders old/new assignments, invalidates tickets/technicians/dashboard caches, and enables
only the newly integrated reassign action. Other feature settings retain their prior values.
V12 widens end_reason to NVARCHAR(1000); API/use-case/schema/demo documentation is updated.

## Main execution flow

1. Manager opens the dialog; it captures the displayed Ticket/version.
2. Server authenticates, checks the role, locks Ticket and checks version/state/current assignment.
3. Server locks the destination profile and re-evaluates eligibility and capacity.
4. One SQL transaction ends and flushes A's assignment, creates B's assignment, touches Ticket,
   and records REASSIGN history. Any failure rolls back all writes.
5. Response returns ASSIGNED, updated version and B's current assignment; UI refetches affected data.
6. A retains historical read access with Start disabled; B receives My Work and can start normally.

## Acceptance criteria

| Criterion | Result | Evidence |
| --- | --- | --- |
| Real frontend/backend demo, correct after refresh | PASS | Edge browser against packaged backend and isolated SQL Server database; live-demo.log/result.json |
| Wrong role, empty/long reason, same technician, ineligible destination | PASS | TicketActionApiContract; frontend dialog tests; real Unicode boundary API check |
| Reject IN_PROGRESS, RESOLVED, CLOSED and missing active assignment | PASS | Parameterized API tests cover every unsupported state; browser verifies IN_PROGRESS action hidden |
| Stale version and rollback on failed replacement | PASS | Shared API contract verifies 409 and unchanged data after assignment/history failure, including after old-row flush |
| Concurrent reassign and reassign versus start | PASS | SQL Server tests verify one winner, stale loser, both start/reassign orders and a single active assignment |
| A historical read access and B operation rights | PASS | API tests and real A/B browser sessions; A start returns 422; B UI start returns 200 |
| API contract, use case and demo document ASSIGNED-only scope | PASS | OpenAPI assertions, docs/API_CONVENTIONS.md, docs/TESTING.md, docs/PROJECT_REPORT.md |
| Capacity/workload remain correct | PASS | SQL Server assign/reassign compete for the last destination slot; A/B active counts checked |

## Commands and results

Commands run from backend/ unless stated otherwise:

- `./mvnw.cmd '-Dtest=TicketWorkflowContractTest,TicketResponseMapperTest,TicketActionApiIntegrationTest' test`:
  195 passed, zero failures/errors/skips (initial targeted verification).
- `./mvnw.cmd verify`: 1081 reported tests, 926 passed, 155 skipped, zero failures/errors.
  Skips are existing environment-gated suites, including SQL Server/MongoDB integration tests.
- With RUN_SQLSERVER_IT=true:
  `./mvnw.cmd '-Dtest=TicketActionSqlServerIntegrationTest,TechnicianWorkSqlServerIntegrationTest,TicketReadSqlServerIntegrationTest,SqlServerMigrationIntegrationTest' test`:
  185 passed, zero failures/errors/skips. Suites: 74 assignment/workflow, 96 technician work,
  13 Ticket read, 2 migrations. Expected unique-index rejection is logged by its negative test.
- `./mvnw.cmd -DskipTests package`: successful executable packaging for isolated browser verification.
- From frontend/: `npm run test -- --maxWorkers=2`: 606 passed across 75 files, zero failures/skips.
- From frontend/: `npm run lint`: passed.
- Prettier check over changed frontend files: passed after formatting TicketDetailPage.jsx.
- From frontend/: `npm run build`: passed, both isolated-API production build for the browser demo
  and final normal build. Existing >500 kB chunk-size advisory remains.
- `node .data/qa/reassignment/live-demo.cjs`: PASS with real headless Edge, packaged Spring backend,
  SQL Server, genuine login sessions and workflow mocks off.
- Additional real API boundary check: reason length 1001 -> 400 with identical persisted detail;
  raw padded 1000-character Vietnamese reason -> 200, persisted trimmed length 1000, one version increment.
- `git diff --check`: passed.

The initial browser harness incorrectly expected the former technician's Start button to be absent.
Existing UI intentionally shows that button disabled; the harness was corrected to assert disabled
and to verify the backend returns 422. The final full browser run passed. An initial formatting
check flagged TicketDetailPage.jsx; formatting was corrected and the check passed.

## Browser evidence

Isolated database: nexora_reassignment_20261030_194159. Accepted browser Ticket: NX-2026-000002.
Versions: 2 before reassign, 3 after reassign, 4 after B starts. Browser errors: none.

Evidence under .data/qa/reassignment/:
- result.json
- manager-after-reassign.png
- historical-technician-a.png
- technician-b-started.png
- live-demo.cjs and backend/frontend logs

Logs under outputs/:
- reassignment-backend-targeted.log
- reassignment-backend-verify.log
- reassignment-sqlserver.log
- reassignment-frontend-targeted.log
- reassignment-frontend-full.log
- reassignment-frontend-lint.log
- reassignment-frontend-build.log
- reassignment-live-demo.log
- reassignment-reason-boundaries.json

## Deviations and remaining limits

No architectural deviations from the approved plan. New behavioral assertions primarily extend
existing shared HTTP/persistence contracts instead of duplicating them in unit tests. Existing
former-technician Start presentation remains disabled, and server authorization is verified.

Reassignment from IN_PROGRESS, override, standalone unassignment and notifications remain outside
this issue's scope. The entire unrelated SQL Server/MongoDB matrix was not rerun; the four relevant
SQL Server suites were explicitly enabled and executed. No acceptance criterion remains unverified.
The existing production bundle-size advisory was not changed.

Temporary isolated backend/preview processes were stopped and the copied runtime JAR removed.
The dedicated test database and verification evidence remain for inspection. Existing application
processes/data and pre-existing untracked documents were preserved. Changes are uncommitted;
no commits or pushes were created.

## Changed files

- `backend/src/main/java/com/nexora/ticket/controller/TicketController.java`: Reassignment endpoint and OpenAPI contract.
- `backend/src/main/java/com/nexora/ticket/dto/request/TicketActionRequestReader.java`: Strict reassignment JSON field/type parsing.
- `backend/src/main/java/com/nexora/ticket/dto/response/TicketDetailResponse.java`: Expose persisted assignment history with the existing public shape.
- `backend/src/main/java/com/nexora/ticket/entity/TicketAssignment.java`: Match the 1000-character reason storage limit.
- `backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java`: Atomic reassignment transaction, locking and version/history update.
- `backend/src/main/java/com/nexora/ticket/service/TicketReadService.java`: Expose persisted assignment history with the existing public shape.
- `backend/src/main/java/com/nexora/ticket/service/TicketResponseMapper.java`: Expose persisted assignment history with the existing public shape.
- `backend/src/main/java/com/nexora/ticket/service/TicketWorkflowContract.java`: Restrict REASSIGN to ASSIGNED.
- `backend/src/test/java/com/nexora/SqlServerMigrationIntegrationTest.java`: Acceptance/regression verification or affected test expectation.
- `backend/src/test/java/com/nexora/ticket/service/TicketResponseMapperTest.java`: Acceptance/regression verification or affected test expectation.
- `backend/src/test/java/com/nexora/ticket/service/TicketWorkflowContractTest.java`: Acceptance/regression verification or affected test expectation.
- `backend/src/test/java/com/nexora/ticket/testsupport/TicketActionApiContract.java`: Acceptance/regression verification or affected test expectation.
- `docs/API_CONVENTIONS.md`: API, schema, scope or demo documentation.
- `docs/DATABASE_SCHEMA.md`: API, schema, scope or demo documentation.
- `docs/PROJECT_REPORT.md`: API, schema, scope or demo documentation.
- `docs/TESTING.md`: API, schema, scope or demo documentation.
- `frontend/src/features/tickets/components/TicketActionPanel.jsx`: Update workflow action presentation.
- `frontend/src/features/tickets/components/TicketAssignmentDialog.jsx`: Current-candidate exclusion and 1000-character reason form.
- `frontend/src/features/tickets/components/TicketDetailSections.jsx`: Render assignment history in existing detail layout.
- `frontend/src/features/tickets/constants/ticketWorkflow.js`: ASSIGNED-only presentation policy and action label.
- `frontend/src/features/tickets/hooks/useTicketWorkflow.js`: Invalidate all affected viewer, technician and dashboard caches.
- `frontend/src/features/tickets/mocks/ticketWorkflowHandlers.js`: Align mock history/state/candidate behavior with the API.
- `frontend/src/features/tickets/mocks/ticketWorkflowHandlers.test.js`: Acceptance/regression verification or affected test expectation.
- `frontend/src/features/tickets/pages/TicketDemoScope.test.jsx`: Acceptance/regression verification or affected test expectation.
- `frontend/src/features/tickets/pages/TicketDetailPage.jsx`: Render assignment history in existing detail layout.
- `frontend/src/features/tickets/pages/TicketWorkflow.test.jsx`: Acceptance/regression verification or affected test expectation.
- `frontend/src/features/tickets/schemas/ticketWorkflowValidation.js`: Reassignment-specific reason limit without changing other limits.
- `frontend/src/features/tickets/schemas/ticketWorkflowValidation.test.js`: Acceptance/regression verification or affected test expectation.
- `frontend/src/shared/config/demoScope.js`: Enable the integrated reassignment action only.
- `backend/src/main/java/com/nexora/ticket/dto/request/ReassignTicketRequest.java`: Strict validated reassignment payload and reason normalization.
- `backend/src/main/resources/db/migration/V12__extend_assignment_end_reason.sql`: Additive reason-width migration.
