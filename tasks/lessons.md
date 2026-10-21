# Lessons

Patterns learned from user corrections. Review at session start; add a rule after every correction.

<!-- Format:
## <short title>
- **Mistake:** what went wrong
- **Rule:** what to do instead
-->

## Confirm update semantics for PUT
- **Mistake:** Made `PUT` a full replacement by default, so fields not sent were cleared (`birthDate` became `null`). The user wanted unsent fields kept. Version 1.0.1 switched to a merge of non-null fields.
- **Change (3.0.0):** The user reversed this for the Web UI. The edit form always sends the whole record, and a merge cannot clear an optional field.
- **Rule:** `PUT` replaces the record; the UI form always sends all fields. Optional fields sent as `null` are cleared. The update request has the same constraints as the create request plus the required `version`. Ask before choosing other semantics for new update endpoints.

## Keep validation constraints portable to JavaScript
- **Mistake:** Used `@Pattern("(?s).*\\S.*")` for "not blank if present". The regex lands in the OpenAPI contract, and the `(?s)` flag is invalid in JavaScript, so the UI's generated Zod schemas break.
- **Rule:** Constraints must translate to JSON Schema that JavaScript understands. Prefer `@NotBlank`, `@Size`, `@Email`; if a `@Pattern` is unavoidable, use syntax valid in both Java and JavaScript.
