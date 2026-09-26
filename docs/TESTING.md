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

Admin bootstrap is a local/demo-only startup concern, not a public provisioning endpoint. Its eventual implementation must satisfy all of these rules:

1. It is enabled only under explicit `local` or `demo` Spring profiles and is disabled by default everywhere else.
2. An explicit enable flag is required; selecting a profile alone is not enough.
3. Email and initial password come from environment variables. No credential, personal email address, or production-like secret is committed.
4. The password is validated and hashed before persistence. Neither the raw password nor its hash is logged.
5. The bootstrap checks for an existing `ADMIN` user inside a transaction and creates one only when none exists. Database uniqueness remains the final protection against concurrent startup.
6. Missing variables fail bootstrap with a clear configuration error only when bootstrap was explicitly enabled.

No bootstrap variables are added to `.env.example` yet because this issue introduces documentation, not executable bootstrap configuration. When authentication startup support is implemented, add clearly non-production placeholders at the same time.

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
