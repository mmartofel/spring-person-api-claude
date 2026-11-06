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

## Test every transition of a relation against the real database
- **Mistake:** `PUT /api/tasks/{id}` loaded the task outside a transaction (`open-in-view` is off), so `save` merged a detached entity and the new assignee became an uninitialized proxy. Mapping the response threw `LazyInitializationException`, and the client got 500 with Spring's default body. Tests covered setting an assignee on create and clearing it on update, never changing it, and the mocked unit tests cannot see JPA proxies.
- **Rule:** Write endpoints that map a relation run in `@Transactional` (`saveAndFlush` on update). Integration tests cover none → A, A → B and A → none. A catch-all handler keeps unexpected errors in Problem Details.

## Advise for the target platform, not the README default
- **Mistake:** For an OpenShift deployment I recommended `skaffold run --port-forward` from the README. On OpenShift the API and Keycloak are exposed by Routes; port forwarding only fits a local cluster.
- **Rule:** Before giving deployment steps, check the target (`oc whoami --show-server`, ingress domain) and use its native exposure (Routes on OpenShift, `-p openshift`). OpenShift-only kinds stay in the `openshift/` overlay, never in the shared `k8s/` base.

## Prove a health check can fail
- **Mistake:** A Compose healthcheck written as `["CMD", "bash", "-c", "<script>"]` was split on spaces by Podman, so it ran `bash -c exec` and reported Keycloak healthy before it started. A new PostgreSQL startup probe with a 5 s delay made the API connect before the database was ready, and the API restarted once.
- **Rule:** Write shell healthchecks as `["CMD-SHELL", "<script>"]`. After adding any probe or healthcheck, check its log shows failures before the service is up (`podman inspect --format '{{json .State.Health}}'`, Warning events), and check that dependent pods show no restarts.

## Read the search output before concluding "nothing exists"
- **Mistake:** A `grep --include=*.sql` in zsh failed with `no matches found` (unquoted glob), so the search ran on nothing. I told the user there was no example data and planned to write new data, while `dev/seed-fake-data.sql` and its README section already existed.
- **Rule:** Quote glob arguments in zsh (`--include='*.sql'`). Treat a shell error in a search as "unknown", not "none". Before claiming something does not exist, check `git ls-files` and the README.
