# Nexora architecture

## Backend modules

```text
com.nexora
├── config         Security, CORS, OpenAPI, persistence configuration
├── common         Cross-cutting exceptions, health, pagination, storage
├── auth           Login, access-token handling and current user
├── user           User, role, permission and technician profile
├── location       Campus, building, floor, room/area
├── asset          Equipment type, equipment and status history
├── category       Incident category, form versions and dynamic validation
├── ticket         Report, assignment, workflow, work log and attachments
├── notification   MongoDB notification documents and delivery state
├── audit          Append-only MongoDB audit events
├── migration      Idempotent legacy SQL-to-MongoDB backfill
└── dashboard      Aggregated metrics and SLA reports
```

Feedback belongs to the ticket module in the MVP. Audit events, ticket comments,
and notifications use Spring Data MongoDB. All strongly relational entities
remain in SQL Server and use JPA/JDBC. Cross-database references are scalar SQL
IDs validated by the service layer, never MongoDB `DBRef` values.

Writes that describe an already committed SQL transaction are published as
application events and persisted to MongoDB with an `AFTER_COMMIT` listener.
MongoDB failures are logged explicitly and cannot roll back the completed SQL
transaction.

Inside each feature, add only the layers it needs:

```text
ticket/
├── controller/
├── dto/request/
├── dto/response/
├── document/
├── entity/
├── repository/
└── service/
```

Keep ticket packages flat by layer. MongoDB comment documents belong in `document/`,
JPA entities and the `TicketStatus`/`TicketAction` enums in `entity/`, comment
repositories and JPA specifications in `repository/`, and comment services and
workflow policy classes directly in `service/`. Keep these responsibilities in
separate classes without introducing nested comment or workflow packages.

Keep the application as a modular monolith for the eight-week MVP. Microservices, Kafka, Redis, and a separate state-machine framework are not required.

## Frontend modules

```text
src/
├── app/
│   ├── router.jsx          Route-to-page mapping
│   ├── providers.jsx       Query and authentication providers
│   ├── queryClient.js
│   └── routes/             ProtectedRoute and RequireRole
├── features/
│   ├── auth/
│   │   ├── components/
│   │   ├── context/
│   │   ├── pages/
│   │   └── schemas/
│   ├── tickets/
│   │   ├── api/
│   │   ├── components/
│   │   ├── constants/
│   │   ├── hooks/
│   │   ├── pages/
│   ├── assets/
│   ├── categories/
│   ├── dashboard/
│   └── home/
├── shared/
│   ├── api/                Shared Axios client and error handling
│   ├── components/         Cross-feature reusable UI
│   ├── hooks/
│   ├── layouts/
│   ├── styles/
│   ├── constants/
│   └── utils/
├── test/                   Shared test setup and mocks
├── App.jsx
└── main.jsx                Browser entry point
```

Rules:

- Pages and UI components do not call Axios directly.
- Use `.jsx` for files that render JSX and `.js` for plain JavaScript modules. The frontend does not use TypeScript or TSX.
- A feature keeps its API calls and query hooks within that feature.
- A page represents a complete routed screen; `components` contains smaller reusable UI pieces.
- TanStack Query owns server state; Context owns only small client state such as the authenticated user.
- Filters, sorting, and pagination should be represented in URL search parameters.
- Route guards improve UX, but Spring Security still enforces authorization.

## Recommended ticket workflow

```text
SUBMITTED → UNDER_REVIEW → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
```

The canonical SQL Server status set also includes `REOPENED`, `REJECTED`, and
`CANCELLED`. Display labels remain separate from these stored/API codes. Ticket
status and action vocabulary, transition rules, actor constraints, mandatory
inputs, and expected side effects are defined by the pure Java contract in
`com.nexora.ticket.service`, using the `TicketStatus` and `TicketAction` enums from
`com.nexora.ticket.entity`. `SUBMIT` starts from the explicit `NEW_REQUEST`
source rather than introducing a non-persisted ticket status.

Workflow changes are action-driven. Later application services select an
approved `TicketAction` and derive its target status from the contract; they do
not accept an arbitrary target status. `CLOSED`, `REJECTED`, and `CANCELLED` are
terminal in the MVP. Assignment changes, status-history writes, timestamps, and
SLA calculation are documented by the contract as expected effects but remain
the responsibility of later transactional services.

## Ticket supporting persistence

`TicketFieldValue`, `TicketStatusHistory`, and `TicketAttachment` map the existing
SQL Server child tables. Ticket has no child collections: query each child through
its own repository. Associations are unidirectional and lazy, with no cascading
persist or delete into Ticket, users, forms, or fields. Referenced rows must be
persisted first. Flyway owns the composite Ticket/form/field foreign keys; the
entities store a fixed `formVersionId` and reject cross-form field references.

Dynamic answers use verbatim `NVARCHAR(MAX)` JSON strings: text/select/date/datetime
are JSON strings, numbers remain numbers, booleans remain booleans, and multi-select
uses an array of option-value strings. Persistence never parses or re-serializes
them. SQL Server checks JSON syntax; later creation services validate field types,
required values, options, and validation rules. Published/archived form data remains
readable. `findByTicketIdOrderByFieldOrder` fetches field metadata and orders by
`displayOrder ASC, field ID ASC` to break display-order ties.

History uses the existing `TicketStatus` and `TicketAction` enums. Initial SUBMIT
has a null source status; actions such as REASSIGN may keep the same status. History
has no mutation methods and its columns are not updateable through JPA. Read it
with `findByTicketIdOrderByChangedAtAscIdAsc`. Generic repository delete methods
are not business operations: callers must treat history as append-only. Later
workflow services must write history in the same SQL transaction as the Ticket
update, never through an `AFTER_COMMIT` listener. Persistence itself does not
automatically enforce that every Ticket update has a corresponding history row.

Attachments store only an opaque storage key, original display filename, content
type, byte count, uploader, and references/timestamp. They contain no binary data
and perform no filesystem access. Original filenames are never paths. A dynamic
IMAGE is a `REPORT_EVIDENCE` attachment linked to an IMAGE field; no scalar value
row is required. Every attachment has the Ticket's form-version ID, even without
a field. `WORK_RESULT` is supported; `COMMENT` is mapped for schema compatibility
and remains P1. Attachment queries filter by Ticket and optionally type, then
order by `createdAt ASC, ID ASC`. Services map entities to DTOs within a transaction
when accessing lazy references because open-in-view is disabled.

This foundation introduces no upload/download endpoint, creation service, workflow
service, or schema migration. Existing FK `NO ACTION` behavior is preserved.

## Dynamic category forms

An administrator creates a category, edits a draft form version, validates it, and publishes it. Published versions are immutable. Each ticket stores the form version used when it was created, so later category changes do not invalidate historical reports.
