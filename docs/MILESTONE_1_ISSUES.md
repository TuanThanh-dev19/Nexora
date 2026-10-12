# Milestone 1 — Foundation Contracts

**Schedule:** September 26–30, 2026  
**Milestone goal:** Establish stable domain contracts, persistence foundations, authentication scaffolding, and shared test infrastructure so that business features can be implemented safely in later milestones.

The identifiers `M1-01` through `M1-10` are planning identifiers. After the issues are created on GitHub, replace `<issue-number>` in branch names and replace dependency references with the actual GitHub issue numbers.

---

# [TASK] Implement User and Role Persistence Foundation

**Planning ID:** M1-01  
**Suggested owner:** Member 1 — Identity & Access  
**Estimate:** 1.5 effective development days

## Objective

Implement the JPA persistence foundation for users, roles, and user-role assignments using the existing SQL Server schema. This foundation will support authentication, authorization, user administration, and technician configuration in later milestones.

## Requirements

- Create JPA entities and supporting value types for `app_users`, `roles`, and `user_roles` without changing the existing database contract.
- Map user status values exactly as `ACTIVE`, `LOCKED`, and `DISABLED`.
- Map role codes exactly as `REQUESTER`, `TECHNICIAN`, `MANAGER`, and `ADMIN`.
- Support multiple roles per user and preserve the assignment metadata defined by the schema, including `assigned_at` and nullable `assigned_by`.
- Map the user `version` column using optimistic locking.
- Treat `normalized_email` as a database-generated, read-only value and do not attempt to write it from application code.
- Create repositories for user lookup by ID and normalized email, role lookup by code, and loading a user together with their assigned roles.
- Add persistence tests for saving and retrieving users, resolving roles, handling multiple roles, and rejecting duplicate normalized email values where the test database supports the constraint.
- Keep entities internal to the persistence/domain layer; do not add REST controllers or expose JPA entities as API responses.

## Expected Outcomes

After this issue is completed:

- The backend can persist and retrieve users and their roles through repositories.
- Entity mappings are compatible with the existing Flyway schema.
- Optimistic locking is available for future user-management operations.
- Authentication work can use a stable user and role model.
- No existing migration or document-database feature is affected.

## Acceptance Criteria

The issue is considered complete when:

- All three relational structures are mapped and repository operations work in automated tests.
- Role and status values match the canonical project vocabulary.
- A user can be loaded with all assigned roles without exposing password hashes outside the domain/persistence boundary.
- The `version` field is configured for optimistic locking.
- No existing Flyway migration has been modified.
- `./mvnw verify` succeeds.
- No secret, password, or real credential is committed.
- The code has been pushed and a Pull Request has been created and reviewed before merge.

## Suggested Branch

```text
feature/<issue-number>-identity-persistence
```

## Dependencies

Depends on M1-03 — Align Canonical Domain Vocabulary. Replace this planning ID with the actual GitHub issue number after issue creation.

## Technical Notes

- Follow the existing package-by-feature structure under `com.nexora.user` and `com.nexora.auth` as appropriate.
- Prefer explicit entity relationships and avoid large bidirectional object graphs.
- Use `OffsetDateTime` or another type that correctly preserves SQL Server `DATETIMEOFFSET` values.
- Do not modify migrations V1–V8. Any unavoidable schema change requires a new migration and prior team agreement.
- Do not log or serialize `password_hash`.
- Do not add user-management endpoints in this issue.

## Definition of Done

- Code is complete and follows the current project structure.
- Repository and mapping tests pass.
- The backend build has no errors.
- Relevant persistence documentation is updated if the implementation introduces a convention.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Build Authentication and Frontend Session Skeleton

**Planning ID:** M1-02  
**Suggested owner:** Member 1 — Identity & Access  
**Estimate:** 1.5 effective development days

## Objective

Create the backend and frontend authentication scaffolding required for the approved bearer-token flow. The issue should replace the unsafe demo-role assumptions with a stable technical foundation while leaving full credential authentication and current-user business behavior for the next milestone.

## Requirements

- Provide a BCrypt `PasswordEncoder` bean for future credential verification.
- Add the HS256 JWT encoding/signing foundation that complements the existing JWT decoder.
- Define the token claims contract: `sub` contains the user ID and the roles claim contains canonical role codes.
- Define request/response DTO contracts for login and current-user data without exposing password hashes or JPA entities.
- Add a JWT-to-Spring-authority converter so canonical role codes are mapped consistently to application authorities.
- Refactor the frontend auth state to store the access token and current-user data in `sessionStorage` using production-oriented keys instead of `nexora.demo-user`.
- Update the shared Axios client to send `Authorization: Bearer <token>` and set `withCredentials` to `false`.
- Preserve the existing behavior that clears the local session after an HTTP `401` response.
- Remove any ability for a user to grant themselves a role from the login UI. If a temporary development path is retained, it must be isolated behind an explicit local-only mechanism and must not accept an arbitrary role from the user.
- Add backend tests for token claims/authority conversion and frontend tests for token restoration, logout, bearer-header injection, and `401` handling.

## Expected Outcomes

After this issue is completed:

- Backend security has reusable password and JWT building blocks.
- Frontend authentication state follows the approved bearer-token storage strategy.
- Protected routes and role checks consume server-provided user roles instead of a user-selected demo role.
- Full login and `/me` integration can be implemented in the next milestone without redesigning auth state.

## Acceptance Criteria

The issue is considered complete when:

- BCrypt password encoding is configured and tested.
- The JWT skeleton can create or validate the agreed claim shape in automated tests.
- Canonical roles are mapped to Spring Security authorities consistently.
- The frontend restores and clears token/session state correctly.
- Authenticated frontend requests include the bearer token and do not enable cookie credentials.
- A `401` response clears the local auth session and redirects or leaves the user unauthenticated safely.
- The normal UI does not allow users to select their own authorization role.
- Backend verification and frontend lint, test, and build commands succeed.
- The Pull Request is reviewed by someone other than its author.

## Suggested Branch

```text
feature/<issue-number>-auth-session-skeleton
```

## Dependencies

- Depends on M1-01 — Implement User and Role Persistence Foundation.
- Depends on M1-03 — Align Canonical Domain Vocabulary.
- Depends on M1-09 — Standardize Shared API Contracts.

Replace these planning IDs with actual GitHub issue numbers after issue creation.

## Technical Notes

- The approved access-token lifetime is eight hours and the MVP does not use refresh tokens.
- Use HS256 and require a secret of at least 32 bytes, consistent with the existing security configuration.
- Never commit the JWT secret or real bootstrap credentials.
- Update `.env.example` only when a new environment variable is introduced.
- Do not implement user administration, refresh-token support, OAuth login, or production deployment secrets in this issue.
- Do not weaken backend authorization to keep the old demo login working.

## Definition of Done

- Backend and frontend scaffolding is complete.
- Security and frontend session tests pass.
- Backend and frontend builds have no errors.
- Configuration and auth documentation are updated where needed.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Align Canonical Domain Vocabulary Across the Application

**Planning ID:** M1-03  
**Suggested owner:** Member 2 — Ticket Intake & Dynamic Forms  
**Estimate:** 1 effective development day

## Objective

Remove vocabulary mismatches between the SQL Server schema, backend domain model, frontend constants, and validation schemas. This issue establishes one canonical set of values before multiple modules begin parallel development.

## Requirements

- Replace the frontend role code `USER` with `REQUESTER` everywhere, including auth validation, route checks, demo/default data, tests, and visible options.
- Define the canonical roles as `REQUESTER`, `TECHNICIAN`, `MANAGER`, and `ADMIN`.
- Add the missing ticket status `REOPENED` and ensure all ticket statuses match the database: `SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`, `REJECTED`, and `CANCELLED`.
- Add the missing dynamic field types `MULTI_SELECT`, `DATETIME`, and `BOOLEAN` so the frontend matches the schema-supported field types.
- Confirm that asset statuses, priorities, form statuses, location types, and user statuses use the same spelling in the database, backend, and frontend.
- Create or update centralized backend enums/value types and frontend constants rather than duplicating string literals in feature components.
- Update affected UI labels, filters, badges, schemas, and tests.
- Add lightweight contract tests that fail when a required canonical value is missing or an obsolete value such as `USER` is reintroduced.

## Expected Outcomes

After this issue is completed:

- Shared domain values are consistent across all application layers.
- New persistence and API work can depend on stable enum names.
- Existing frontend components display all supported statuses and field types safely.
- No database migration is needed for vocabulary alignment.

## Acceptance Criteria

The issue is considered complete when:

- No application source file uses `USER` as an authorization role.
- `REOPENED` is supported by ticket constants and relevant presentation components.
- All nine dynamic field types supported by SQL Server are represented in shared constants.
- Backend enums and frontend constants use the exact database values.
- Tests cover the canonical role, ticket status, and dynamic field type sets.
- Existing frontend pages still render without runtime errors.
- Backend verification and frontend lint, test, and build commands succeed.
- The Pull Request is reviewed before merge.

## Suggested Branch

```text
feature/<issue-number>-canonical-domain-vocabulary
```

## Dependencies

None

## Technical Notes

- Treat the existing SQL Server constraints and approved decision record as the source of truth.
- Do not rename database values or edit existing Flyway migrations.
- Keep display labels separate from stored/API codes so future localization does not alter contracts.
- Do not implement transition behavior or dynamic-form validation logic in this issue.
- Coordinate this change early because several other M1 issues depend on the canonical constants.

## Definition of Done

- Canonical constants and enums are implemented.
- Affected tests and UI components are updated.
- Backend and frontend builds have no errors.
- Contract documentation is updated if necessary.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Implement Dynamic Form Persistence Foundation

**Planning ID:** M1-04  
**Suggested owner:** Member 2 — Ticket Intake & Dynamic Forms  
**Estimate:** 2 effective development days

## Objective

Implement the relational persistence foundation for incident categories and versioned dynamic forms. The result will support published-form retrieval, ticket submission, and form administration in later milestones without implementing those business workflows yet.

## Requirements

- Create JPA entities and repositories for `incident_categories`, `category_form_versions`, `field_definitions`, `field_options`, and `sla_policies`.
- Map category asset policy, default priority, active state, location requirement, creator, and timestamps.
- Map form status, version number, publishing metadata, equipment requirement, minimum attachment count, default skill reference, and default SLA policy reference.
- Map all canonical field types and preserve `validation_rules` as valid JSON without silently changing its content.
- Map field and option ordering and enforce the existing uniqueness boundaries in the domain/persistence model.
- Provide repository queries for active categories and for resolving the single current `PUBLISHED` form for a category.
- Ensure ordered retrieval of fields and options using their `display_order` values.
- Add tests covering category/form relationships, ordered fields/options, active filtering, and published-form lookup.
- Keep cross-module references such as creator and default skill narrowly mapped; do not introduce large bidirectional graphs or business validation in entity callbacks.

## Expected Outcomes

After this issue is completed:

- Category and form data can be retrieved through stable repositories.
- A published form can be loaded with its fields and options in deterministic order.
- SLA and cross-module reference IDs are preserved for future ticket and assignment logic.
- Later form-management and ticket-creation issues can build on tested mappings.

## Acceptance Criteria

The issue is considered complete when:

- All required tables are mapped without modifying existing migrations.
- Active categories and a category's current published form can be queried through repositories.
- Fields and options are returned in deterministic display order.
- `validation_rules` remains valid JSON and is not exposed as an unvalidated API contract.
- Repository tests cover a category with a published form, multiple fields, and select options.
- No form editor, publish service, or ticket-creation endpoint is added as part of this issue.
- `./mvnw verify` succeeds.
- The Pull Request receives schema and integration review before merge.

## Suggested Branch

```text
feature/<issue-number>-dynamic-form-persistence
```

## Dependencies

Depends on M1-03 — Align Canonical Domain Vocabulary. Replace this planning ID with the actual GitHub issue number after issue creation.

## Technical Notes

- Published and archived forms are immutable at the business layer; this issue only establishes persistence mappings.
- `default_sla_policy_id` is the authoritative SLA reference for new tickets in later milestones.
- `default_skill_id` may be mapped as a narrow association or reference ID until the workforce model is merged; coordinate the contract with M1-06 and avoid duplicate `Skill` entities.
- Do not implement form cloning, publishing, archiving, ticket field values, or attachment validation in this issue.
- Do not modify migrations V1–V8.

## Definition of Done

- Entities, repositories, and persistence tests are complete.
- All tests and the backend build pass.
- Persistence conventions are documented where needed.
- No unrelated architecture or dependency change is included.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Define Ticket Workflow Transition Contract

**Planning ID:** M1-05  
**Suggested owner:** Member 3 — Workflow & Technician Operations  
**Estimate:** 1.5 effective development days

## Objective

Represent the approved ticket lifecycle as an explicit, testable domain contract. This foundation prevents later workflow endpoints from implementing inconsistent or unrestricted status changes.

## Requirements

- Define canonical `TicketStatus` and `TicketAction` types in the ticket workflow package.
- Represent every approved transition: submit, begin review, reject, cancel before assignment, assign, start work, unassign, reassign, cancel after assignment, resolve, close, force close, and reopen.
- Record the allowed source status, target status, permitted actor roles, and whether a non-blank reason or other mandatory input is required for each action.
- Treat `CLOSED`, `REJECTED`, and `CANCELLED` as terminal statuses with no outgoing MVP transitions.
- Prevent a generic "set any status" path from becoming part of the domain contract.
- Add unit tests for every valid transition and representative invalid status/action combinations.
- Add tests for required-reason metadata and terminal-state rejection.
- Keep the transition contract independent from controllers and persistence so later services can reuse it.

## Expected Outcomes

After this issue is completed:

- The complete MVP ticket lifecycle is represented in one testable location.
- Workflow service and endpoint issues can depend on an approved transition model.
- Invalid or unsupported state changes can be rejected consistently in later milestones.
- The contract is ready for optimistic-locking and status-history integration without implementing those operations yet.

## Acceptance Criteria

The issue is considered complete when:

- Every approved MVP action has an explicit transition definition.
- All source/target states and actor roles match the approved transition and permission matrices.
- Terminal statuses do not allow outgoing actions.
- Unit tests cover all valid transitions and invalid-transition examples.
- No generic status update endpoint or workflow controller is introduced.
- The backend build and tests succeed.
- Workflow contract documentation is updated if code naming differs from existing documentation.
- The Pull Request is reviewed before merge.

## Suggested Branch

```text
feature/<issue-number>-ticket-workflow-contract
```

## Dependencies

Depends on M1-03 — Align Canonical Domain Vocabulary. Replace this planning ID with the actual GitHub issue number after issue creation.

## Technical Notes

- Place `TicketStatus` and `TicketAction` under `com.nexora.ticket.entity`; place the workflow contract and supporting policy types directly under `com.nexora.ticket.service`.
- The contract should express policy, not mutate a ticket entity in this issue.
- Required side effects such as assignment changes, status history, timestamps, and SLA recalculation should be represented as documented expectations, not implemented prematurely.
- `DUPLICATE` resolution behavior is outside the MVP even though the database constraint retains the value.
- Avoid adding dependencies on Spring MVC to the workflow domain contract.

## Definition of Done

- The transition contract is complete.
- Unit tests cover valid and invalid transitions.
- The backend build has no errors.
- Documentation is updated where required.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Implement Technician Workforce Persistence Foundation

**Planning ID:** M1-06  
**Suggested owner:** Member 3 — Workflow & Technician Operations  
**Estimate:** 1.5 effective development days

## Objective

Implement the persistence model for technician profiles, skills, technician skill assignments, and service-area coverage. This foundation will support manual assignment eligibility and technician capacity rules in later milestones.

## Requirements

- Create JPA entities and repositories for `technician_profiles`, `skills`, `technician_skills`, and `technician_service_areas`.
- Map the technician profile as an extension of an existing user, using the user ID as the profile's primary key.
- Map `available`, `max_active_tickets`, timestamps, and optimistic-lock `version` values.
- Map technician skill proficiency from 1 through 5 and preserve assignment timestamps.
- Map technician service areas to existing locations.
- Provide repository queries for loading a technician with skills and service areas, finding active skills, and checking direct configured coverage for a location.
- Add persistence tests for profile creation, multiple skills, proficiency validation, service-area assignments, and version handling.
- Coordinate the shared `Skill` model with the dynamic-form module so there is only one authoritative entity/repository definition.
- Do not implement capacity calculation, descendant-location traversal, assignment selection, or override behavior in this issue.

## Expected Outcomes

After this issue is completed:

- Technician configuration data can be persisted and retrieved reliably.
- Skills and service areas are available for later assignment eligibility logic.
- The workforce model reuses the identity and location foundations instead of duplicating them.
- Future technician administration and assignment issues have stable repository contracts.

## Acceptance Criteria

The issue is considered complete when:

- Technician profile, skill, technician-skill, and service-area mappings match the existing SQL Server schema.
- A technician profile cannot exist without its referenced user.
- Skill proficiency and positive capacity constraints are represented and tested.
- A technician can be loaded with all assigned skills and configured service areas.
- Optimistic locking is configured for technician-profile updates.
- No assignment algorithm or technician-management API is introduced.
- `./mvnw verify` succeeds.
- The Pull Request receives identity, schema, and integration review as applicable.

## Suggested Branch

```text
feature/<issue-number>-technician-persistence
```

## Dependencies

- Depends on M1-01 — Implement User and Role Persistence Foundation.
- Depends on M1-03 — Align Canonical Domain Vocabulary.
- Depends on M1-07 — Implement Location and Equipment Type Persistence Foundation.

Replace these planning IDs with actual GitHub issue numbers after issue creation.

## Technical Notes

- A user must eventually have the `TECHNICIAN` role to be operationally eligible, but role eligibility belongs to a later service issue.
- Service-area descendant traversal is a business query/service concern and is out of scope here.
- Avoid duplicating the `AppUser`, `Location`, or `Skill` mappings across packages.
- Do not add automatic assignment behavior; MVP assignment is manual with eligibility validation.
- Do not modify migrations V1–V8.

## Definition of Done

- Workforce entities and repositories are complete.
- Persistence tests pass.
- The backend build has no errors.
- Shared-model decisions are documented if needed.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Implement Location and Equipment Type Persistence Foundation

**Planning ID:** M1-07  
**Suggested owner:** Member 4 — Location & Asset Management  
**Estimate:** 1.5 effective development days

## Objective

Implement the persistence foundation for the campus location hierarchy and equipment-type reference data. These references are required by asset management, technician coverage, and future ticket submission flows.

## Requirements

- Create JPA entities and repositories for `locations` and `equipment_types` using the existing schema.
- Map canonical location types: `CAMPUS`, `BUILDING`, `FLOOR`, `ROOM`, and `AREA`.
- Map the self-referencing parent/child location relationship without creating unsafe recursive serialization behavior.
- Map location and equipment-type active states, codes, names, descriptions, timestamps, and equipment brand/model fields.
- Provide repository queries for active root locations, active direct children, lookup by unique code, and active equipment types.
- Ensure repository methods return deterministic ordering suitable for later selector and tree APIs.
- Add tests for a multi-level location hierarchy, code lookup, active filtering, and equipment-type persistence.
- Do not expose entities directly through REST responses.

## Expected Outcomes

After this issue is completed:

- The backend can retrieve the campus hierarchy through repositories.
- Equipment types are available as stable reference data.
- Asset and technician service-area issues can reuse the same location model.
- Existing schema and migrations remain unchanged.

## Acceptance Criteria

The issue is considered complete when:

- Location and equipment-type entities match the SQL Server schema.
- A hierarchy containing at least campus, building, floor, and room levels can be persisted and queried in tests.
- Active filtering and code lookup work correctly.
- The mapping does not produce recursive `toString`, equality, or serialization failures.
- Repository queries use deterministic ordering.
- No CRUD controller or location-management UI is added.
- `./mvnw verify` succeeds.
- The Pull Request receives schema and integration review before merge.

## Suggested Branch

```text
feature/<issue-number>-location-equipment-persistence
```

## Dependencies

Depends on M1-03 — Align Canonical Domain Vocabulary. Replace this planning ID with the actual GitHub issue number after issue creation.

## Technical Notes

- Follow the package boundaries under `com.nexora.location` and `com.nexora.asset`.
- Keep entity relationships persistence-focused; future REST APIs must use DTOs.
- Do not implement recursive descendant business queries unless required for a repository test.
- Coordinate the location entity contract with M1-06 and M1-08 before those issues begin.
- Do not modify migrations V1–V8.

## Definition of Done

- Entities, repositories, and tests are complete.
- The backend build has no errors.
- Shared location conventions are documented if needed.
- No unrelated schema or architecture change is included.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Implement Asset and Asset Status History Persistence Foundation

**Planning ID:** M1-08  
**Suggested owner:** Member 4 — Location & Asset Management  
**Estimate:** 1.5 effective development days

## Objective

Implement the persistence model for campus assets and their status history. This foundation will support asset administration, ticket asset selection, and audited status changes in later milestones.

## Requirements

- Create JPA entities and repositories for `assets` and `asset_status_history` using the existing SQL Server schema.
- Map canonical asset statuses: `ACTIVE`, `UNDER_MAINTENANCE`, `OUT_OF_SERVICE`, and `RETIRED`.
- Map asset code, optional serial number, equipment type, location, description, purchase date, warranty date, timestamps, and optimistic-lock `version`.
- Map status-history source status, target status, required reason, changing user, optional related ticket, and change timestamp.
- Provide repository queries for asset code lookup, assets by location/status, assets by equipment type, and status history ordered newest first.
- Add tests for asset persistence, unique business identifiers where supported, location/equipment relationships, optimistic locking, and ordered history retrieval.
- Keep status-changing business logic and history creation out of entity setters; those operations belong to a later transactional service issue.

## Expected Outcomes

After this issue is completed:

- Assets can be stored and queried through stable repositories.
- Asset status history can be retrieved in deterministic chronological order.
- The model is ready for future status-transition and ticket-eligibility services.
- Existing location and identity mappings are reused consistently.

## Acceptance Criteria

The issue is considered complete when:

- Asset and status-history mappings match the current schema, including fields introduced by V6.
- Assets can be queried by location and eligible status values.
- Optimistic locking is configured for asset updates.
- Status history retains actor, reason, optional ticket reference, and timestamp data.
- Automated tests cover relationships and newest-first history retrieval.
- No asset CRUD API, status-change endpoint, or UI is added.
- `./mvnw verify` succeeds.
- The Pull Request receives schema and integration review before merge.

## Suggested Branch

```text
feature/<issue-number>-asset-persistence
```

## Dependencies

- Depends on M1-01 — Implement User and Role Persistence Foundation.
- Depends on M1-03 — Align Canonical Domain Vocabulary.
- Depends on M1-07 — Implement Location and Equipment Type Persistence Foundation.

Replace these planning IDs with actual GitHub issue numbers after issue creation.

## Technical Notes

- Reuse the existing `Location`, `EquipmentType`, and user entities; do not create package-local duplicates.
- Only `ACTIVE` and `UNDER_MAINTENANCE` assets are eligible for new tickets later, but that business validation is outside this persistence issue.
- Avoid cascade settings that could delete reference data or history accidentally.
- Do not edit applied migrations. Any schema correction requires a separate migration and explicit review.
- Do not implement status changes through direct generic entity updates.

## Definition of Done

- Asset entities, repositories, and tests are complete.
- Optimistic-lock and history-query tests pass.
- The backend build has no errors.
- Documentation is updated if new persistence conventions are introduced.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Standardize Shared API Error, Pagination, and Concurrency Contracts

**Planning ID:** M1-09  
**Suggested owner:** Member 5 — Ticket Explorer, Dashboard & Integration  
**Estimate:** 1.5 effective development days

## Objective

Create reusable API contracts for errors, pagination, sorting validation, and optimistic-concurrency failures. These shared conventions must be stable before business endpoints are implemented by multiple module owners.

## Requirements

- Keep `/api/v1` as the business API base path.
- Standardize RFC 9457/Spring `ProblemDetail` responses with stable problem types, readable titles/details, and field-level validation errors where applicable.
- Add handling for malformed requests, missing resources, business-rule violations, access denied, unauthenticated requests, and optimistic-lock conflicts.
- Define a reusable zero-based page response with `content`, `page`, `size`, `totalElements`, and `totalPages`.
- Define the default page size as 20 and maximum page size as 100.
- Provide a reusable approach for endpoint-specific sort allowlists and reject unknown sort/filter values with HTTP `400`.
- Define the convention that mutating request DTOs carry an expected entity `version` and stale writes return HTTP `409 Conflict`.
- Add focused MVC/unit tests that verify response status, content type, stable problem type, and field-error structure.
- Add OpenAPI examples or shared documentation for the page and error shapes.

## Expected Outcomes

After this issue is completed:

- Feature teams can implement endpoints using one error and pagination format.
- Clients can distinguish validation, authorization, business-rule, not-found, and stale-write failures.
- Ticket search and dashboard work can reuse a stable list contract.
- API conventions are verified by tests rather than existing only in documentation.

## Acceptance Criteria

The issue is considered complete when:

- Validation errors return HTTP `400` with a stable problem type and field-error object.
- Missing resources return `404`, business-rule violations return the agreed status, and stale writes return `409`.
- Pagination metadata exactly matches the approved response shape.
- Page-size bounds and invalid sort values are rejected consistently.
- Authentication/authorization error responses do not leak sensitive implementation details.
- Tests verify the shared contracts.
- OpenAPI or project documentation contains reusable examples.
- `./mvnw verify` succeeds and the Pull Request is reviewed.

## Suggested Branch

```text
feature/<issue-number>-shared-api-contracts
```

## Dependencies

Depends on M1-03 for canonical enum values used in shared examples. Core error and pagination work may begin in parallel, but the issue must not close until the vocabulary contract is stable.

## Technical Notes

- Extend the existing `GlobalExceptionHandler`; do not introduce a second competing exception format.
- Do not expose stack traces, SQL messages, JWT details, or internal exception class names.
- Keep page DTOs independent from JPA entities.
- Business endpoints remain out of scope; use focused test controllers or unit tests when a concrete endpoint is required for contract verification.
- Avoid adding a response envelope around non-list success responses unless separately approved.

## Definition of Done

- Shared API contracts are implemented and tested.
- OpenAPI/project documentation is updated.
- The backend build has no errors.
- No sensitive information is exposed by error responses.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

---

# [TASK] Establish Shared Test Fixtures and Demo Bootstrap Strategy

**Planning ID:** M1-10  
**Suggested owner:** Member 5 — Ticket Explorer, Dashboard & Integration  
**Estimate:** 1.5 effective development days

## Objective

Create a consistent test-data foundation and document the safe local/demo bootstrap strategy. This issue reduces duplicated setup across module tests and prepares the project for authentication and reference-data integration in the next milestone.

## Requirements

- Define reusable test builders or fixtures for users, roles, locations, equipment types, categories, form versions, fields, options, skills, and SLA policies as their M1 mappings become available.
- Keep fixtures deterministic and independent so tests do not depend on execution order or shared mutable state.
- Provide representative users for all four canonical roles without embedding real credentials.
- Document the local/demo Admin bootstrap contract: it runs only when no Admin exists, reads credentials from environment variables, and is disabled by default outside local/demo profiles.
- Add placeholder/example bootstrap variables to `.env.example` only if the application configuration introduced by this issue requires them; use clearly non-production example values.
- Create or improve shared integration-test support for loading the Spring test profile and resetting owned test data safely.
- Confirm that the fixture approach works with the fast H2 test profile and does not prevent the existing SQL Server Testcontainers verification from running.
- Add a short testing document or section explaining when to use unit tests, repository tests, SQL Server migration tests, and MongoDB integration tests.
- Run the complete backend and frontend quality commands and record/fix any M1 integration failures within the scope of this issue.

## Expected Outcomes

After this issue is completed:

- Module owners can create consistent test data without copying large setup blocks.
- Local/demo bootstrap requirements are documented without committing a password.
- Cross-module persistence tests use compatible reference data.
- The project has a repeatable quality baseline for M2 development.

## Acceptance Criteria

The issue is considered complete when:

- Shared fixtures/builders exist for every M1 entity that has been merged and needs cross-module test data.
- Fixture-created users use canonical roles and safe test-only password values.
- Tests remain independent and can run in any order.
- The bootstrap strategy is documented, profile-restricted, environment-driven, and contains no real secret.
- The fast backend suite passes.
- Existing SQL Server and MongoDB integration tests remain runnable under their documented flags.
- Frontend lint, tests, and production build pass.
- CI is green or any external CI blocker is documented clearly before milestone review.
- The Pull Request is reviewed before merge.

## Suggested Branch

```text
feature/<issue-number>-test-fixtures-bootstrap
```

## Dependencies

- Depends on M1-01 — Implement User and Role Persistence Foundation.
- Depends on M1-04 — Implement Dynamic Form Persistence Foundation.
- Depends on M1-07 — Implement Location and Equipment Type Persistence Foundation.
- Depends on M1-09 — Standardize Shared API Contracts.

The fixture design may begin earlier, but the final implementation must use the merged entity contracts. Replace planning IDs with actual GitHub issue numbers after issue creation.

## Technical Notes

- This issue owns shared integration infrastructure, not the feature-specific tests that each module owner must write.
- Never use production-like secrets or personal email addresses in fixtures.
- Do not make tests depend on Docker unless they are explicitly marked as integration tests.
- Preserve the existing opt-in flags for SQL Server and MongoDB Testcontainers tests.
- Do not implement the production Admin provisioning endpoint in this issue.

## Definition of Done

- Shared test fixtures and bootstrap documentation are complete.
- Backend and frontend quality commands pass.
- CI configuration remains usable.
- No secret or real credential is committed.
- A Pull Request has been created and reviewed.
- The PR links this issue using:

```text
Closes #<issue-number>
```

