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

## Check your own identifiers before blaming the infrastructure
- **Mistake:** Typed the repo owner as `mmartofe` instead of `mmartofel`, got 404s from the GitHub API, and reported a stale `gh` token and a GitHub REST outage. Fallbacks were documented in CLAUDE.md for a problem that did not exist.
- **Rule:** When an API that worked before returns 404, first diff the request against the last working one (owner, repo, SHA) and take identifiers from the source (`git remote get-url origin`), not from memory. Only then suspect auth or the service.

## Build from clean before trusting a green result
- **Mistake:** Added a test helper with the same signature as an existing one. The build looked fine because classes compiled by the IDE (Eclipse compiler) were already in `target`, so the duplicate surfaced only later.
- **Rule:** Verify with `mvn clean verify`. After a scripted edit (sed, perl, Python), check that it actually changed the file (`git diff --stat`) before building.
