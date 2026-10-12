# Flyway migrations for SQL Server

The current baseline is:

```text
V1__create_user_and_role_tables.sql
V2__create_location_and_asset_tables.sql
V3__create_dynamic_category_tables.sql
V4__create_ticket_workflow_tables.sql
V5__seed_reference_data.sql
V6__complete_erd_support_tables.sql
V7__seed_skill_and_sla_reference_data.sql
```

Rules for the team:

- Write T-SQL for SQL Server (`IDENTITY`, `BIT`, `NVARCHAR`, `DATETIMEOFFSET`).
- Do not use PostgreSQL-only syntax such as `JSONB`, `SERIAL`, `ILIKE`, `RETURNING`, or `ON CONFLICT`.
- Never edit a migration after it has been merged or applied by another teammate. Add the next version instead.
- Flyway owns application tables. The Docker init script only creates the empty `nexora` database.
- Every schema pull request must be verified against a fresh SQL Server database, not only H2.
- Coordinate the next migration number in the team board before creating a file.

Published category form versions are immutable. Changing a dynamic form requires a new form version so old tickets remain readable.

`V6` completes the ERD with technician profiles, skills, service areas,
form-version defaults, shared SLA policies, audit logs, and optional reviewed violation/appeal
tables. `V7` seeds skills and SLA policies, then connects the four starter forms
to those defaults.
