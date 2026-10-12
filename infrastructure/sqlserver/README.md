# SQL Server local infrastructure

The root `compose.yaml` runs the pinned SQL Server 2022 Developer image `2022-CU26-ubuntu-22.04`. The one-shot `sqlserver-init` service creates the `nexora` database after SQL Server becomes healthy. An `Exited (0)` init service is the expected successful state.

Spring Boot and Flyway remain the only source of tables, constraints, indexes, and reference data. Do not add application schema creation to the container startup script; add immutable migrations under `backend/src/main/resources/db/migration`.

The complete setup and troubleshooting guide is at `docs/SQLSERVER_DOCKER_GUIDE.md`.

The ERD-to-physical-schema mapping is documented at `docs/DATABASE_SCHEMA.md`.
After Flyway runs, `verify-schema.sql` can be executed with `sqlcmd` or SSMS to
check that all expected tables and constraints exist.

`nexora_full_schema.sql` is the standalone, single-file V1-V8 bundle for an
empty database. It is provided for manual SSMS/sqlcmd use and submission only;
do not run it on the same database that Spring Boot/Flyway manages.
