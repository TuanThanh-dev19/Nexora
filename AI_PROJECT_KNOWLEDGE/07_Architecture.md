# Architecture

> **Decision update (2026-09-25):** the MVP now uses internal email/password authentication, HS256 bearer JWT, local persistent file storage and SQL Server only. See `DEC-028` through `DEC-031`; MongoDB is explicitly out of MVP.

## Current Architecture

### Overall Style

- One repository containing a backend, frontend, documentation and local SQL Server infrastructure.
- Intended runtime is a feature-first modular monolith, not microservices.
- Frontend and backend share feature boundaries such as auth, ticket, asset, category and dashboard.
- Business implementation is not yet present end-to-end; current state is foundation/scaffold.

```text
React SPA (Vite, port 5173)
        │ /api via dev proxy
        ▼
Spring Boot REST API (port 8080)
        │ JPA/Flyway configuration
        ▼
SQL Server 2022 (port 1433)
```

### Backend Technology and Structure

- Java 21; Spring Boot 4.1.1; Maven wrapper.
- Spring MVC, Data JPA, Validation, Security, OAuth2 Resource Server, Actuator and Springdoc.
- Microsoft SQL Server JDBC runtime and Flyway SQL Server support.
- Feature packages: `auth`, `user`, `location`, `asset`, `category`, `ticket`, `dashboard`; most currently contain only `package-info.java` markers.
- Cross-cutting code currently implemented:
  - application entry point;
  - security/CORS/JWT decoder configuration;
  - OpenAPI metadata;
  - public health controller;
  - base exceptions and `ProblemDetail` handler.
- Missing business layers: JPA entities, repositories, services, DTOs and business controllers.

### Frontend Technology and Structure

- React 19 with JavaScript/JSX; Vite 8; React Router 8.
- TanStack Query for intended server state; Axios shared client; Context for demo auth state.
- React Hook Form + Zod; React Bootstrap/Bootstrap; Recharts dependency.
- `src/app`: router, providers, query client and route guards.
- `src/features`: auth, tickets, assets, categories, dashboard and home starter code.
- `src/shared`: API utilities, components, layout, config, hooks, styles and utilities.
- Current UI is a navigable scaffold. Ticket data hook/API sample is not used by the ticket page because the backend endpoint does not exist.

### Database and Migration Architecture

- SQL Server 2022 Developer container pinned to `2022-CU26-ubuntu-22.04`.
- Docker init service creates only the empty `nexora` database.
- Flyway V1-V7 creates all application tables, constraints, indexes and seed data.
- Hibernate `ddl-auto=validate`; applied migrations must not be modified.
- `nexora_full_schema.sql` is for standalone/manual initialization only, not alongside Flyway.

### Authentication and Authorization Setup

Implemented foundation:

- backend is stateless and can validate HS256 bearer JWTs;
- public/Swagger/health paths are allowed, all other paths require authentication;
- method security is enabled;
- frontend has protected-route and role-route components.

Not implemented:

- authentication controller/service;
- credential verification against `app_users`;
- token issuance/refresh/revocation;
- conversion of token claims into the intended role authorities;
- current-user business API;
- real frontend token/session integration.

Frontend login is explicitly a UI-only demo stored in `sessionStorage` and must not be treated as real authentication.

### API Communication Setup

- Frontend Axios base URL defaults to `/api/v1`.
- Vite proxies `/api` to backend port 8080.
- Axios client listens for HTTP 401 and clears demo auth state.
- `withCredentials: true` is set, but the approved cookie/bearer strategy is TBD.
- Backend CORS allows configured origins and credentials.

### Testing and CI

- Backend context-load test uses H2 with Flyway disabled.
- Optional SQL Server Testcontainers test verifies 29 tables, seed counts, selected FKs/indexes and migration V7.
- Frontend has a Home page unit test and shared jsdom setup.
- GitHub Actions runs Maven verify and frontend npm lint/test/build on pull requests and pushes to main.
- Business behavior/authorization/workflow tests are not present.

### Configuration and Dependencies

- Root `.env.example` provides SQL Server, backend and frontend variables; `.env` is gitignored.
- Backend config includes DB URL/user/password defaults, Flyway, UTC JDBC time, CORS origin and JWT secret override.
- Frontend `.env.example` only defines `VITE_API_BASE_URL`.
- `spring-boot-starter-data-mongodb` is declared but no MongoDB usage/config exists; intention is TBD.

## Intended Architecture

The repository documentation states these intended boundaries:

- keep the eight-week MVP as a modular monolith;
- use package-by-feature on backend and frontend;
- controllers handle HTTP, services enforce business rules/transactions, repositories handle persistence;
- use DTOs instead of exposing entities;
- use explicit tested Java policy code for ticket transitions;
- TanStack Query owns server state; Context owns only small client state;
- pages/components do not call Axios directly;
- filters/sort/pagination should be represented in URL search parameters;
- backend authorization remains authoritative even when route guards exist.

**Status: Designed, not fully implemented.**

## Architecture TBD

1. Authentication/identity strategy and token lifecycle.
2. Exact mapping from JWT claims to database users/roles.
3. Attachment/object storage solution.
4. Transaction boundaries and event handling for ticket changes, notifications and audit.
5. Pagination/filtering/error/optimistic-lock API conventions.
6. Whether MongoDB is intentionally part of the architecture; current schema is entirely SQL Server-based.
7. Deployment platform, production secret management, TLS, database hosting and backup.
8. Observability beyond health checks: logging, tracing, metrics and alerting.
9. Whether optional P1 modules are included in delivery scope.

## Current Architecture Risks / Alignment Items

- Axios is configured with credentials, while backend is currently configured as bearer-JWT resource server; final transport strategy is unconfirmed.
- H2 context test does not execute Flyway; real migration coverage depends on optional SQL Server Testcontainers run.
- `application.yml` contains development fallback secrets/passwords. Documentation says production values must be externalized; production secret handling is TBD.
