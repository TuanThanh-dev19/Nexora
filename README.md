# Nexora

Nexora is a campus incident reporting, maintenance workflow, and equipment tracking platform. The repository is a modular monolith containing one Spring Boot REST API and one React single-page application.

## Technology

- Backend: Java 21, Spring Boot 3.5, Maven, Spring Security, JPA, Spring Data MongoDB, Bean Validation, Flyway.
- Databases: Microsoft SQL Server 2022 Developer for relational data and MongoDB 8 for document data.
- Frontend: ReactJS 19, JavaScript/JSX, Vite, React Router, Axios, TanStack Query, React Bootstrap.
- Tests: JUnit/Spring Test, SQL Server and MongoDB Testcontainers, Vitest/React Testing Library.
- Local infrastructure: Docker Compose with separate persistent SQL Server and MongoDB volumes.

## Repository layout

```text
nexora/
├── backend/          Spring Boot REST API and Flyway migrations
├── frontend/         React SPA
├── docs/             Architecture and team documentation
├── infrastructure/   Database initialization and verification support
├── .github/          CI and pull-request template
├── compose.yaml      Local SQL Server 2022 and MongoDB 8
└── .env.example      Local environment template
```

The backend uses package by feature (`ticket`, `asset`, `category`, ...). The frontend follows the same feature boundaries under `src/features`, allowing teammates to own vertical slices end to end.

## First run

Requirements: Java 21, Node.js 22.12+ or 24+, Docker Desktop, and Git. Docker Desktop must be running.

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up -d
docker compose ps -a
```

Expected result:

- `sqlserver` becomes `healthy`.
- `mongodb` becomes `healthy`.
- `sqlserver-init` becomes `Exited (0)` after creating the `nexora` database. This is normal.
- Spring Boot runs Flyway through V8 and creates the MongoDB collections and indexes.

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start the frontend in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

- Frontend: http://localhost:5173
- Backend health endpoint: http://localhost:8080/api/v1/public/health
- Actuator health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

The root `.env` file is read by both Docker Compose and Spring Boot when the
backend is started from either the repository root or `backend/`. Environment
variables and IDE run-configuration values still override the file.

## Database connection

Use these local settings in SQL Server Management Studio or another SQL client:

```text
Server: localhost,1433
Authentication: SQL Server Authentication
Login: sa
Password: value of MSSQL_SA_PASSWORD in .env
Database: nexora
Trust server certificate: enabled for local development only
```

Local JDBC URL:

```text
jdbc:sqlserver://localhost:1433;databaseName=nexora;encrypt=true;trustServerCertificate=true
```

Local MongoDB connection (use the values from `.env`):

```text
mongodb://<MONGO_USERNAME>:<MONGO_PASSWORD>@localhost:<MONGO_PORT>/nexora?authSource=admin
```

SQL Server remains authoritative for users, tickets, categories, locations,
assets, assignments, and other relational data. MongoDB is authoritative for
`audit_events`, `ticket_comments`, and `notifications`; it stores SQL IDs as
plain `Long` references and does not use `DBRef`.

See [docs/SQLSERVER_DOCKER_GUIDE.md](docs/SQLSERVER_DOCKER_GUIDE.md) for the complete team setup, daily commands, migrations, troubleshooting, and safe reset procedure.

## Quality commands

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm run lint
npm run test
npm run build
```

The fast backend suite uses H2 in SQL Server compatibility mode. To verify real T-SQL migrations with Testcontainers, Docker Desktop must be running:

```powershell
cd backend
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd -Dtest=SqlServerMigrationIntegrationTest test
Remove-Item Env:RUN_SQLSERVER_IT

$env:RUN_MONGODB_IT='true'
.\mvnw.cmd -Dtest=MongoPersistenceIntegrationTest test
Remove-Item Env:RUN_MONGODB_IT
```

## Upgrading an existing database with legacy document rows

Do not remove or reset the existing SQL Server volume. First inspect the source counts:

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d nexora -Q "SELECT (SELECT COUNT(*) FROM dbo.audit_logs) auditLogs, (SELECT COUNT(*) FROM dbo.ticket_comments) ticketComments, (SELECT COUNT(*) FROM dbo.notifications) notifications"'
```

If all three counts are zero, start the upgraded backend normally; V8 records
the empty-state decision by safely dropping the three tables. If any count is
non-zero, run the one-time backfill before V8:

```powershell
cd backend
$env:MONGO_BACKFILL_ENABLED='true'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.target=7"
```

Wait for the `MongoDB backfill verified` log (it prints all three before/after
counts), then stop the process with `Ctrl+C`, clear the temporary setting, and
start normally so Flyway can apply V8:

```powershell
Remove-Item Env:MONGO_BACKFILL_ENABLED
.\mvnw.cmd spring-boot:run
```

The backfill uses a unique `legacySqlId`, so rerunning it updates the same
documents instead of duplicating them. V8 compares the verified counts with
the current SQL counts and refuses to drop anything if they differ.

## Development rules

- Never commit `.env`, passwords, JWT keys, or production credentials.
- Do not expose JPA entities directly from controllers; use request/response DTOs.
- Controllers handle HTTP, services enforce business rules, repositories handle persistence.
- The backend is the final authority for permissions, dynamic validation, and status transitions.
- Use Flyway migrations and keep `ddl-auto=validate`; never use `ddl-auto=update`.
- Never modify an applied migration. Add the next migration version instead.
- Create one branch per task, for example `feat/ticket-workflow` or `fix/category-validation`.
- Open a pull request to `main` and ask at least one teammate to review it.
- `trustServerCertificate=true` is only for the self-signed local container; deployed environments must use a trusted certificate.

Further documentation:

- [Hướng dẫn cài đặt và chạy dự án](HUONG_DAN_CHAY_DU_AN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Shared API contracts](docs/API_CONVENTIONS.md)
- [SBA301 project structure](docs/PROJECT_STRUCTURE_SBA301.md)
- [GitHub setup](docs/GITHUB_SETUP.md)
- [SQL Server and Docker team guide](docs/SQLSERVER_DOCKER_GUIDE.md)
- [ERD to SQL Server schema mapping](docs/DATABASE_SCHEMA.md)

> Version note: this project is pinned to Spring Boot 3.5.16 and Java 21. The frontend uses ReactJS with JavaScript/JSX and has no TypeScript compilation step.
