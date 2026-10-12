# Category and dynamic form API (v1)

All routes require a Bearer token. Administration routes require `ADMIN`; `REQUESTER`, `TECHNICIAN` and `MANAGER` receive `403`. Anonymous requests receive `401`.

## Reference endpoints

| Method | Route                                            | Result                                                                                                                    |
| ------ | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/categories?active=true`                 | Active categories, ordered by name then ID. Reference reads always exclude inactive categories, even with `active=false`. |
| GET    | `/api/v1/categories/{categoryId}/published-form` | Exactly the current `PUBLISHED` form for an active category.                                                              |
| GET    | `/api/v1/sla-policies?active=true`               | Active SLA policies ordered by name then ID. `active=false` includes inactive policies.                                   |

An unknown or inactive category returns `404`. An active category without a published form returns `422` with the detail `This category has no published form.` Drafts and archived forms are never returned by the reference form route.

Published form responses contain `schemaVersion: 1`, `id`, `category` (including `assetPolicy`, `locationRequired`, and `defaultPriority`), `versionNumber`, `status`, `revision`, `requiresEquipment`, `minAttachments`, `defaultSkillId`, `defaultSlaPolicyId`, resolved `defaultSkill` and `defaultSlaPolicy`, timestamps, and `fields`.

Each field includes `id`, `fieldKey`, `label`, `fieldType`, `helpText`, `placeholder`, `required`, `active`, `displayOrder`, structured `validationRules`, and `options`. Each option includes `id`, `optionValue`, `optionLabel`, `displayOrder`, and `active`. Reference responses exclude inactive fields/options; admin responses include them. Both lists are ordered by `displayOrder` then ID, so ties are deterministic. Entities are mapped to DTOs inside read transactions; responses have no JPA navigation cycles.

Ticket creation must use the published form's `defaultSlaPolicyId`, not the legacy category `slaHours` value. The published form ID and its field/option IDs remain unchanged when another version is published.

## Administration endpoints

| Method   | Route                                                        | Result                                                                                 |
| -------- | ------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| GET/POST | `/api/v1/admin/categories`                                   | List all categories / create a category (`201`).                                       |
| PUT      | `/api/v1/admin/categories/{categoryId}`                      | Update category metadata and active state.                                             |
| GET      | `/api/v1/admin/categories/{categoryId}/form-versions`        | Version summaries ordered by version number.                                           |
| POST     | `/api/v1/admin/categories/{categoryId}/form-versions/drafts` | Create an empty draft with the next server-assigned version number (`201`).            |
| GET/PUT  | `/api/v1/admin/form-versions/{formVersionId}`                | Read any version / atomically replace a draft's settings, fields and options.          |
| POST     | `/api/v1/admin/form-versions/{formVersionId}/publish`        | Publish a valid draft and archive the previous published form in the same transaction. |
| POST     | `/api/v1/admin/form-versions/{formVersionId}/archive`        | Archive a published form.                                                              |
| GET      | `/api/v1/admin/skills`                                       | Active skills for the default-skill selector.                                          |

Category request example:

```json
{
  "code": "IT_SUPPORT",
  "name": "IT support",
  "description": "Device and network incidents",
  "assetPolicy": "OPTIONAL",
  "locationRequired": true,
  "defaultPriority": "NORMAL",
  "active": true
}
```

Category codes are immutable after creation. An update cannot forbid assets while the published form requires equipment. Category activation/deactivation and other metadata remain editable. Updating category metadata does not mutate stored version fields/options.

Draft replacement example:

```json
{
  "revision": 0,
  "requiresEquipment": false,
  "minAttachments": 0,
  "defaultSkillId": 1,
  "defaultSlaPolicyId": 2,
  "fields": [
    {
      "fieldKey": "impact",
      "label": "Who is affected?",
      "fieldType": "SELECT",
      "helpText": "Choose the affected group",
      "placeholder": null,
      "required": true,
      "active": true,
      "displayOrder": 0,
      "validationRules": {},
      "options": [
        {
          "optionValue": "individual",
          "optionLabel": "Just me",
          "displayOrder": 0,
          "active": true
        }
      ]
    }
  ]
}
```

Draft saves validate storage constraints, unique keys/options, type compatibility and rule shape. Drafts may omit default skill/SLA and may contain a select without active options while being designed. Publication additionally requires an active category, an active default skill and SLA, compatible equipment/asset settings, and at least one active option for each select field. An empty form is allowed for categories requiring only the standard ticket fields. Inactive fields remain in the immutable definition but are excluded from new-ticket reference data.

`PUT` replaces the entire draft structure in one transaction. Draft field and option IDs may change; clients must use the response after saving. Published/archived definitions cannot be edited. There are at most 100 fields and 200 options per field. Keys and option values are unique ignoring case to match SQL Server's default collation. Field keys use letters, digits and underscores and begin with a letter; option values use letters, digits, dots, underscores and dashes.

Publish/archive bodies contain the revision most recently returned by the server:

```json
{ "revision": 1 }
```

Every draft save (including field-only edits), publication and archive increments `revision`. Missing revisions return `400`; stale revisions return `409`. Each category's write operations acquire the same database row lock, serializing draft numbering, edits, category changes and publication. The existing filtered unique index additionally prevents two published forms. The previous version is flushed as archived before the new version is published; failure of either step rolls back both.

## Controlled validation rules

`schemaVersion: 1` defines the following allowlist. Rules are JSON objects, never JSON strings, arrays or null. Unsupported keys, duplicate JSON keys, incompatible rules and invalid values are rejected explicitly. Rule values are preserved; they are never stripped or replaced with `{}`. JSON formatting/whitespace is not part of the HTTP contract. Persistence continues to store the JSON in `validation_rules`.

| Field type      | Allowed rules                                                                                                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TEXT, TEXTAREA  | `minLength`, `maxLength`: non-negative integers; `pattern`: a valid regular expression, maximum 500 characters. Use expressions compatible with Java and JavaScript.                |
| NUMBER          | `min`, `max`: finite numbers; `step`: positive finite number.                                                                                                                       |
| DATE            | `min`, `max`: ISO calendar dates (`YYYY-MM-DD`).                                                                                                                                    |
| DATETIME        | `min`, `max`: ISO local date-times, e.g. `2026-10-07T09:30:00`.                                                                                                                     |
| MULTI_SELECT    | `minItems`, `maxItems`: non-negative integers.                                                                                                                                      |
| IMAGE           | `minFiles`: non-negative integer; `maxFiles`, `maxSizeBytes`: positive integers; `allowedTypes`: nonempty array containing `image/jpeg`, `image/png`, `image/webp`, or `image/gif`. |
| SELECT, BOOLEAN | Empty object `{}`.                                                                                                                                                                  |

All minima must be less than or equal to their matching maxima. Integer rules are limited to the signed 32-bit range. Existing seeded image rules (`minFiles`, `maxFiles`, `allowedTypes`) remain supported.

Validation failures use the shared Problem Details contract. Storage/input validation returns `400`; business/publication validation returns `422`. `fieldErrors` maps paths such as `fields[0].fieldKey`, `fields[0].options[1].optionValue`, `fields[0].validationRules.maxLength`, `defaultSkillId` or `category.active` to actionable messages. The UI shows an error summary and field/option errors inline. Unexpected concurrent revisions use the shared `409` stale-write problem.

## UI and verification

Admin navigation: **Categories → Form versions → Create draft → Save draft → Publish form → Confirm publish**. Saved data is reloaded from the API on refresh. The responsive editor includes field/option reorder controls, active toggles, type-specific validation, a requester preview, pending/empty/error/success states, stale-revision recovery, and publication/archive confirmation. Known invalid forms and unsaved changes disable publication. Published and archived form content is read only.

```powershell
cd backend
.\mvnw.cmd --batch-mode verify
# Real SQL Server migrations, concurrency and rollback (Docker required):
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd --batch-mode '-Dtest=CategoryFormSqlServerIntegrationTest,SqlServerMigrationIntegrationTest' test
Remove-Item Env:RUN_SQLSERVER_IT

cd ..\frontend
npm run lint
npm run test
npm run build
```

Use Java 21 and Node 24.15+ (the repository's current dependencies require a recent Node runtime). Tests cover role/authentication boundaries, active filtering, current-publication lookup, deterministic ordering, rule/type validation, immutable versions, stale revisions, concurrent draft creation/publication, publication rollback, option replacement with SQL Server foreign keys, and the UI draft/edit/save/confirm/publish/refresh flow through MSW.
