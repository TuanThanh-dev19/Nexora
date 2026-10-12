# UI Pages

> **Decision update (2026-09-25):** the approved MVP page inventory and UI quality baseline are recorded in `DEC-033` and `DEC-034` of `00_Approved_Decisions.md`. This does not change the current placeholder implementation status.

## Current UI Status

Functional business UI implementation has not started end-to-end. A React shell and several starter/placeholder routes exist, but they are not backed by business APIs.

## Confirmed Current Routes/Pages

| Route | Current content | Access in starter | Implementation status |
|---|---|---|---|
| `/` | Home/overview cards describing intended modules | Public | Implemented static scaffold |
| `/login` | Email + demo-role form; saves demo user in session storage | Public | Implemented demo only; not real auth |
| `/dashboard` | Four metric cards with em-dash placeholders | Protected by demo session | Placeholder; no metrics API |
| `/tickets` | Search/status controls and Create button; no data/results/form | Protected by demo session | Starter UI; no backend integration |
| `/assets` | Placeholder description | `MANAGER` or `ADMIN` in demo guard | Placeholder |
| `/categories` | Placeholder description | `MANAGER` or `ADMIN` in demo guard | Placeholder |
| `/administration` | Placeholder description | `ADMIN` in demo guard | Placeholder |
| `/unauthorized` | Static 403 page | Public route | Implemented static scaffold |
| `*` | Redirect to `/` | Any | Implemented routing behavior |

Other current UI components include status badges, filters, dynamic field preview, loading/empty states and common layout. Their existence does not mean corresponding features are implemented.

## Documented UI Intent

- SPA routing and route guards.
- Complete URL-level pages under each feature.
- Feature-specific API calls and query hooks rather than direct Axios calls from components.
- URL search parameters for filter/sort/pagination.
- Dynamic forms driven by category/form definitions.
- Backend remains the authority for authorization and validation.

**Status: Designed.** No complete approved wireframe/navigation map exists in the repository.

## Planned / Suggested Pages

> **Suggested - not an approved requirement and not implemented.** Final page split should follow confirmed user journeys and team scope.

- Real sign-in/current-user experience.
- Role-appropriate ticket list and ticket detail.
- Ticket creation driven by selected category/form version.
- Explicit review, assignment, work-log, resolution and feedback views/actions.
- Location/equipment type/asset administration.
- Category/form-version editor and publishing view.
- User/role/technician/skill/service-area administration.
- Dashboard/reporting views after metrics are confirmed.
- Optional notifications, audit viewer and violation/appeal pages only if scope is approved.

## UI Information Required

1. Approved navigation map and page inventory per role.
2. User journeys for requester, technician, manager and admin.
3. Wireframes/design system, branding and responsive targets.
4. Ticket list columns, search/filter/sort/pagination behavior and visibility scope.
5. Ticket creation steps and dynamic-form interaction rules.
6. Ticket detail sections and allowed actions by state/role.
7. Attachment upload/preview behavior.
8. Dashboard metrics and chart definitions.
9. Empty/loading/error/success confirmation behavior.
10. Accessibility and localization requirements.
11. Whether optional P1 screens are part of MVP.
