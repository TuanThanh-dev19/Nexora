# Category and Dynamic Form UI — issue #53

The existing `/categories`, `/categories/:categoryId/forms` and `/categories/forms/:formVersionId` pages use the real Category/Form APIs documented in [CATEGORY_FORM_API.md](CATEGORY_FORM_API.md). No new CRUD implementation or production mock routes are introduced. Administration remains restricted to ADMIN; the backend remains authoritative.

## Editing and preview

Category dialogs submit all existing CategoryRequest fields. Category codes are immutable after creation. Draft fields/options can be added, removed and reordered. API `displayOrder` is restored on load and current array order is serialized on save. Inactive fields/options remain editable but are excluded from preview. JSON rules are validated without dropping unknown properties or silently repairing invalid/duplicate keys.

The preview renders TEXT, TEXTAREA, NUMBER, SELECT, MULTI_SELECT, BOOLEAN, DATE, DATETIME and IMAGE. Sample values are separate from the draft; selecting an image only displays local file metadata, with no upload. Desktop shows preview next to the editor. Tablet/mobile switches between editor and preview without unmounting draft state.

Only DRAFT is editable. Save uses `revision`; publish/archive require confirmation. Dirty forms warn on route navigation and refresh. Automatic focus/reconnect refetch is disabled for the opened form. A 409 blocks further saves/publish and offers explicit reload confirmation. Failed reload retains local edits; a successful reload replaces them with the latest server revision. Published/archived content remains read only.

## Deferred submission contract

Backend IMAGE/DATETIME alignment belongs to the subsequent dynamic-values/evidence validation issue. This change preserves the currently accepted administration rules, including existing image MIME types and local ISO datetime bounds. Preview defaults to JPEG/PNG and a multiple-file chooser (up to five under the planned Ticket contract), The planned submission contract is 5 MiB per image/five images per Ticket; it is documented here rather than presented as an enforced preview limit. It does not enforce upload limits or submit Ticket values. DATETIME uses a browser local datetime control; offset conversion and submission validation remain part of the Ticket/backend alignment work. Existing persisted rules are never rewritten to the planned contract.

## Validation

MSW/component tests cover category creation/update request shapes, dirty category dialogs, loading/error/retry/empty states, draft save/publish/remount, draft navigation/refresh warnings, publish field errors, stale revision and failed/successful reload, immutable published/archive transitions, role guards, field/option reorder/remove, API ordering and all nine preview controls. Production API calls are not mocked by the implementation.

Browser layout checks use test API responses at widths 1280, 768 and 390 pixels. These verify rendering and responsive switching, not live SQL Server integration.
