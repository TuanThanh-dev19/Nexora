# Testing and local demo data

Tests must be deterministic, independent, and safe to run in any order. Shared fixtures live under `backend/src/test/java/com/nexora/testsupport`; feature-specific data remains with its feature tests.

## Choosing a test type

| Test type | Use it for | Infrastructure |
| --- | --- | --- |
| Unit test | Policies, validation, mapping, and pure business logic | None |
| Repository test | JPA mappings, queries, constraints, and optimistic locking | Fast H2 profile via `@NexoraJpaTest` |
| Application integration test | Spring wiring spanning multiple components | `@NexoraIntegrationTest`, which activates `test` |
| SQL Server migration test | Real T-SQL, Flyway migrations, computed columns, and SQL Server indexes | Docker, opt in with `RUN_SQLSERVER_IT=true` |
| MongoDB integration test | Mongo documents, indexes, and repository behavior | Docker, opt in with `RUN_MONGODB_IT=true` |

Normal tests must not require Docker. SQL Server and MongoDB tests retain separate opt-in flags so either suite can be selected explicitly.

## Shared fixtures

`UserFixtures` creates a fresh `AppUser` on every call. It provides representative users for `REQUESTER`, `TECHNICIAN`, `MANAGER`, and `ADMIN`, uses only `example.test` addresses, and uses an obvious test-only password hash. `RoleFixtures` creates canonical roles. `CanonicalIdentityFixtures` persists all four users, roles, and assignments with a fixed timestamp.

`OwnedTestDataReset.resetIdentityData()` deletes owned rows in foreign-key-safe order: user-role assignments, users, then roles. A reset helper must list its owned tables explicitly; broad schema drops, Flyway clean, and deletion of another module's data are not allowed.

Fixture coverage follows merged Java mappings:

| Contract | Current fixture status |
| --- | --- |
| Users, roles, user-role assignments | Implemented |
| Locations, equipment types | Add when their JPA entities merge |
| Categories, form versions, fields, options | Add when their JPA entities merge |
| Skills, SLA policies | Add when their JPA entities merge |

Database migrations alone are not treated as entity contracts. When one of the pending mappings merges, its module owner adds a pure builder plus persistence support here without introducing shared mutable state.

## Local/demo Admin bootstrap contract

Admin bootstrap is implemented by `AdminBootstrapRunner` and `AdminBootstrapService` as a local/demo-only startup concern, not a public provisioning endpoint:

1. It is enabled only under explicit `local` or `demo` Spring profiles and is disabled by default everywhere else.
2. An explicit enable flag is required; selecting a profile alone is not enough.
3. Email and initial password come from environment variables. No credential, personal email address, or production-like secret is committed.
4. The email must be valid and at most 254 characters after trimming. The password must be nonblank, at least 8 characters, and at most 72 UTF-8 bytes. It is BCrypt-hashed before persistence. Neither the raw password nor its hash is logged.
5. The bootstrap locks the seeded `ADMIN` role row and checks for an existing `ADMIN` assignment inside a transaction. It creates a user and role assignment together only when no Admin exists, including locked or disabled Admins. An existing account with the same normalized email is never promoted. Database uniqueness remains the final protection against concurrent user creation.
6. Missing variables fail bootstrap with a clear configuration error only when bootstrap was explicitly enabled.

`.env.example` documents `ADMIN_BOOTSTRAP_ENABLED=false`, with blank `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` values. The real local values must remain outside version control. A successful repeat startup skips creation without changing any Admin password or status.

From `backend/` in PowerShell, after SQL Server and MongoDB are available:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ADMIN_BOOTSTRAP_EMAIL = 'admin@example.test'
$env:ADMIN_BOOTSTRAP_PASSWORD = '<choose a local password>'
.\mvnw.cmd spring-boot:run
```

The log reports `Local/demo Admin bootstrap created an account.` or that an Admin already exists. The account can then log in through `POST /api/v1/auth/login`. Clear the bootstrap flag and credentials from the terminal after stopping the backend; this bootstrap does not provide password reset or public registration.

## Quality commands

From `backend`:

```powershell
.\mvnw.cmd verify
```

Optional container suites:

```powershell
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd '-Dtest=SqlServerMigrationIntegrationTest,UserPersistenceSqlServerIntegrationTest' test
Remove-Item Env:RUN_SQLSERVER_IT

$env:RUN_MONGODB_IT='true'
.\mvnw.cmd -Dtest=MongoPersistenceIntegrationTest test
Remove-Item Env:RUN_MONGODB_IT
```

From `frontend`:

```powershell
npm run lint
npm run test
npm run build
```

## Ticket supporting persistence

`TicketSupportingEntitiesTest` covers constructor guards, persisted references,
cross-form rejection, positive attachment sizes, opaque keys, display filenames,
nullable history source/reason, and UTC default timestamps.

`TicketSupportingPersistenceRepositoryTest` uses `@NexoraJpaTest` to verify raw
JSON string/number/boolean/array/date/datetime round-trips (including Unicode,
escaping, long text, and numeric precision), ordered field metadata/history,
attachment filtering, timestamp/order ties, IMAGE attachments without scalar rows,
empty lists, uniqueness, and reference-safe deletion. Fixtures are local to these
tests and reuse `UserFixtures.builder()`.

H2 generates its schema with Flyway disabled; it cannot prove SQL Server's
`ISJSON(value_json, VALUE)` or the complete composite foreign-key contract.
`TicketSupportingPersistenceSqlServerIntegrationTest` uses the existing SQL Server
2022 Testcontainer, Flyway V1-V8 and Hibernate schema validation. It checks those
constraints, exact column names/types/lengths/nullability, DATETIMEOFFSET(7)
precision/offsets, attachment purposes, ordering, and FK `NO ACTION` behavior.
Negative cases use JDBC to bypass entity guards, each in a separately rolled-back
test transaction. A commit/rollback test verifies that Ticket update and history
participate in one SQL transaction; its cleanup targets only IDs it owns.

From `backend`, run the focused fast tests:

```powershell
.\mvnw.cmd '-Dtest=TicketSupportingEntitiesTest,TicketSupportingPersistenceRepositoryTest,CanonicalDomainVocabularyTest' test
```

Run real SQL Server checks with Docker available:

```powershell
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd '-Dtest=TicketSupportingPersistenceSqlServerIntegrationTest,TicketPersistenceSqlServerIntegrationTest,SqlServerMigrationIntegrationTest' test
Remove-Item Env:RUN_SQLSERVER_IT
```

The backend CI already sets `RUN_SQLSERVER_IT=true`. Full regression/build
verification is `.\mvnw.cmd verify` with that flag set; normal local tests remain
independent of Docker when it is absent.

## Ticket read API and UI (#35)

`TicketReadApiIntegrationTest` and `TicketReadSqlServerIntegrationTest` execute the
same HTTP contract against H2 and SQL Server. `TicketReadFixtures` creates fresh
users/reference rows, archived/current form versions, values for all field types,
IMAGE evidence, ordered history, public attachment metadata, and repeated past
assignments. Setup commits before MockMvc calls and cleanup removes only owned
rows. Canonical role seed rows are reused and retained.

Coverage includes all four roles and their union, SQL-scoped counts/pagination,
stale JWT roles and revoked roles, ACTIVE checks, safe missing/invisible detail,
stored form/option history, null optional references, metadata confidentiality,
OpenAPI, and unchanged Ticket version/timestamps. Repository and unit/MVC tests
cover strict query validation and ensure children are not queried before access.
`AuthServiceCurrentUserTest` verifies malformed, missing, non-positive and overflowing
JWT subjects are rejected before reading users. Ticket service tests verify invalid
or inactive identities and users without read roles never query Tickets or children.
`TicketResponseMapperTest` checks public DTOs, stored inactive options, ordered data,
raw JSON precision, optional assets and metadata confidentiality.
`TicketPersistenceSqlServerIntegrationTest` also rejects a stale detached Ticket
after a competing SQL update increments its optimistic-lock version.

From `backend`:

```powershell
.\mvnw.cmd '-Dtest=TicketListQueryTest,TicketReadServiceTest,TicketResponseMapperTest,TicketControllerMvcTest,TicketVisibilityRepositoryTest,TicketReadApiIntegrationTest,SharedApiContractMvcTest' test
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd verify
Remove-Item Env:RUN_SQLSERVER_IT
```

Frontend MSW/Testing Library tests cover list/detail loading, success/empty/error,
URL restoration and Back/Forward, sort/filter/page changes, historical dynamic
values, metadata-only attachments, location lookup, and private cache cancellation
on logout/login/401. All mock Ticket data lives under feature test support and is
never substituted into application responses. Test retry delays are zero to keep
network/5xx retry assertions deterministic; production retains Query's defaults.
List queries are validated by the backend. Frontend tests assert that Axios receives
all URL query entries, including duplicates, unknown keys, empty values and large ID
strings; a backend 400 is displayed without silently changing the URL. Controls read
display values separately from the raw request, pagination uses the response page,
and explicit reset clears the query. A network failure remains a network error even
when the URL query is invalid. The small detail-ID guard remains for immediate UX.

From `frontend`, run `npm run lint`, `npm run test`, and `npm run build`.

## Manager review and assignment UI/mock (#52)

The UI/mock contract is documented in `docs/API_CONVENTIONS.md`. The backend
workflow/eligibility endpoints and current-assignment detail extension remain
future work. Existing real detail reads do not depend on them.

From `frontend`, run the focused tests:

```powershell
npm run test -- src/features/tickets
```

New validation, MSW HTTP, and rendered UI tests cover Manager/Admin/mixed roles
and all ticket states; exact version payloads including zero; required reasons;
candidate search/filter/pagination and empty/error states; soft overrides and
hard blockers; consistent status/history/version/assignment/workload changes;
same-status reassignment; 400/403/404/409/422 and network/server failures; pending
locks; changed capacity refresh; stale dialog snapshots and explicit reload; and
mock-disabled/production presentation. Existing read, auth/cache, and vocabulary
tests remain regression checks. Run full `npm run lint`, `npm run test`, and
`npm run build` before handoff. These mocks do not prove backend authorization or
transactional concurrency.

### Development demo

Start the real backend/authentication normally, then enable only ticket mocks:

```powershell
$env:VITE_TICKET_WORKFLOW_MOCK='true'
npm run dev
# After stopping Vite:
Remove-Item Env:VITE_TICKET_WORKFLOW_MOCK
```

Sign in with an ACTIVE Manager/Admin account (see `docs/LOCAL_ACCOUNTS_GUIDE.md`).
Authentication is unchanged: mock requests obtain the current user from real
`/auth/me`; mock history uses that user's identity. Ticket data is fictional and
the panel/success notices explicitly identify simulation. Other APIs pass through.
The worker starts before React mounts. Never use demo fixtures as real fallback
responses when mocks are off.

Fixture tickets: 10 SUBMITTED, 11 UNDER_REVIEW, 12 ASSIGNED, 13 IN_PROGRESS,
14 REOPENED, 15 RESOLVED, 16 REJECTED, 17 CLOSED, 18 CANCELLED. Candidate 21 is
eligible, 22 unavailable, 23 skill mismatch, 24 at capacity, 25 coverage mismatch,
26 inactive, 27 missing TECHNICIAN role, 28 missing profile. Active assignments
and counts change together after each action.

The development browser console provides deterministic scenarios:

```javascript
window.ticketWorkflowMock.failNextAction(400) // Also accepts 403, 404, 409
window.ticketWorkflowMock.reset() // Then reload the page to refresh query data
```

409 simulates a complete competing review/assignment/unassignment before rejecting
the pending action, so reload shows a consistent newer version. Capacity errors
can be demonstrated by submitting an eligible selection after capacity changes in
the HTTP/UI tests; normal mismatch selection exposes required override input.
Reloading the page also resets the in-memory fixtures. There is no persistence.

`npm run preview` serves a production build: workflow stays unavailable even if
the mock flag is true. Production never starts/imports the browser mock module.
Verify that the real detail/history remain readable and no future candidate/action
request is made. Responsive table/modal styling uses existing React Bootstrap
components; browser/mobile visual QA should also be performed with real login.
