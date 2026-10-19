# Ticket submission validation — NEX-048 / #80

This extends the validator introduced with NEX-046 (#78 / PR #82). It adds no validation endpoint or duplicate creation workflow. `TicketSubmissionValidator` reads supplied metadata/file streams and returns checked answers/image mappings; it never writes the database, allocates numbers/history or stores temporary/permanent files. The creation service owns fetching references/404s, locks and transaction completion.

## Contract

Call `validate(currentPublishedForm, definitions, options, request, orderedFiles)` inside the creation transaction. Metadata must belong to the supplied form. The validator checks category activity and submitted version/category itself; the caller must supply the actual current form under transaction locks. A foreign category is 400 at `formVersionId`; a different/non-published version is 409 `form-version-changed`. Unknown/inactive/duplicate keys and scalar IMAGE entries are invalid even for empty values.

The result contains checked field definitions and exact JSON answers plus a file-index-to-IMAGE map. A valid file index absent from that map denotes general evidence; every file still needs exactly one valid attachment mapping. Result maps are immutable. Optional missing/null/blank/empty-array answers produce no value row; required answers reject them. False and zero are values. Nonblank text, including leading/trailing spaces and newlines, is preserved exactly; length and full-string regex apply to the stored value.

NUMBER accepts JSON numbers only and uses BigDecimal remainder: `(value - (min ?? 0)) % step == 0`. No double tolerance decides server modulo. Frontend step checks use BigInt decimal integers/scales, rejecting values just off a step. Browser inputs serialize as JSON numbers; server never coerces numeric/boolean strings. DATE stays YYYY-MM-DD and checks a real calendar day without timezone conversion. DATETIME requires offset/Z on the wire; UI datetime-local serializes to an instant. Bounds are inclusive.

FileStorageValidator exposes stream-only multipart signature checks and typed InvalidFileException.Reason values. Existing path checks reuse the same signature code. Global validateCount still rejects an empty collection; submission calls it only for nonempty files. Error classification never parses exception messages. Images are JPEG/PNG, 1–5,242,880 bytes, at most five. Counts use unique file indices; field-linked evidence counts once globally. Field limits only tighten global limits.

- 400: type/required/value/selection, mapping/count, empty/wrong-signature image.
- 413: global or field byte limit.
- 415: global or field unsupported MIME.
- 409: `https://nexora.app/problems/form-version-changed`.
- 422: `https://nexora.app/problems/form-configuration-incompatible`.

Scalar and field-image count errors use dynamicValues.<fieldKey>; mapping/file errors use attachments[index]; total count uses attachments. Published configurations are never rewritten. Publish validator and existing editor reject combined active IMAGE minimums above five. Drafts may remain incomplete; publish requires correction. Historical reads retain the old stored form.

## Reusable fixture and checks

TicketSubmissionFixtures creates valid, database-free form/request input for validator consumers. `backend/src/test/resources/contracts/ticket-submission-values.json` is shared by backend and frontend tests for false/zero, decimal steps, regex/newlines, real dates, equivalent offsets and active selections.

Additional tests cover optional normalization, preserved whitespace, strict keys/types, exact decimals beyond double range, zero/five images, duplicate/out-of-range mappings, limits/MIME/signatures and stale/legacy/corrected configurations. The creation HTTP/SQL contract also verifies no value rows for optional nulls, foreign-category 400, typed upload errors, rollback and historical reads. SQL Server runs that contract in CI. Responses never expose file paths, stacktraces or exception classes.

See [CREATE_TICKET_API.md](CREATE_TICKET_API.md) and [CATEGORY_FORM_API.md](CATEGORY_FORM_API.md) for API/configuration contracts.
