# Create Ticket UI

Branch: `feature/50-create-ticket-ui`. Route: `/tickets/new`, guarded for REQUESTER.

The screen uses active categories, the current published form, active location children and paged eligible assets. Historical list filters continue to include inactive references. Fields and options are filtered and ordered by `displayOrder`, then ID. Asset selection follows the published form's category policy and `requiresEquipment`.

Submission uses `POST /api/v1/tickets` with multipart `ticket` (an `application/json` Blob) and repeated `files` parts. The JSON contains only category/form/location/asset references, title, description, dynamic values and file-index mappings. IMAGE fields have attachment mappings only. NUMBER zero, BOOLEAN false and arrays retain their types. DATE stays `YYYY-MM-DD`; DATETIME is converted from the browser's local date-time to an ISO instant ending in `Z`. This follows the proposed submission contract; the existing published form API still has legacy local DATETIME rules, interpreted in browser local time until backend alignment.

Evidence allows JPEG/PNG, at most five files total and 5 MiB per file; stricter field rules also apply. Every selected file has one mapping. Thumbnail URLs are released when removed or unmounted. A category change requires confirmation before removing dependent references, values and evidence; title/description survive. Location changes confirm removal of a selected asset. Navigation and refresh warn about unsent input.

HTTP 400 field errors, 401/403, missing references, 409, 413/415 and network failures keep unsent input. On 409, submission stops until the user explicitly reloads the published form. Reload preserves matching field keys/types, filters removed options and drops photos linked to removed IMAGE fields. Remaining data is validated again. A successful real response navigates to its detail, displays the ticket number and invalidates ticket/dashboard queries.

## Development preview

Run the frontend dev server and open `/dev/create-ticket/new`. This route is excluded from production. It has its own QueryClient and injected in-memory fixture API; it does not intercept or mutate real API calls. Its scenario selector covers success, 400, 401, 403, 404, 409, 413, 415 and network failures. Mock success opens a fixture detail with the same returned ticket number and an explicit notice that nothing was persisted.

The real create endpoint remains a backend dependency. A failed request never displays a saved-ticket success message. Server-side creation, submission validation and durable image storage are outside this UI change.

## Verification

- Contract tests cover all nine field types, active ordering, zero/false, dates, select constraints, evidence rules, conflict reconciliation and omission of server-owned fields.
- MSW tests inspect actual multipart content, authentication, reference requests and Problem Details status preservation.
- Page tests cover pending/double submit, success navigation, backend field errors, dependent resets, conflict reload and thumbnail cleanup. Existing route tests verify unauthorized roles cannot access `/tickets/new`.
- Visual QA exercises desktop (1280px), tablet (768px) and mobile (390px) submission using the isolated preview.
