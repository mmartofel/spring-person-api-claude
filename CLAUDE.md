# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

### 1. Plan Mode Default
- Enter plan mode for ANY not-trivial task (3+ steps or architectural decisions)
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until the mistake rate drops
- Review lessons at session start for a project

### 3. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 4. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes. Don't overengineer
- Challenge your own work before presenting it

### 5. Skills usage
- Use skills for any task that requires a capability
- Skills come from the `spring-boot-template` plugin (e.g. `spring-boot-template:spring-boot`, `spring-boot-template:testing-patterns`, `spring-boot-template:conventions`)
- Invoke skills with natural language
- Each skill is one independent capability

### 6. Subagents usage
- Use subagents liberally to keep the main context window clean
- Subagents come from the `spring-boot-template` plugin (e.g. `spring-boot-template:spring-boot-engineer`, `spring-boot-template:code-reviewer`, `spring-boot-template:devops-engineer`)
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution on a given tech stack

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

## Project General Instructions

- Always use the latest versions of dependencies.
- Always write Java code as the Spring Boot application.
- Always use Maven for dependency management.
- Always create test cases for the generated code both positive and negative.
- Always generate the CircleCI pipeline in the .circleci directory to verify the code.
- Minimize the amount of code generated.
- The Maven artifact name must be the same as the parent directory name: `spring-person-api`.
- Use semantic versioning for the Maven project, starting at `1.0.0`. Each time you generate a new version, bump PATCH by default, MINOR for a new backward-compatible feature, and MAJOR for a breaking API change.
- Target Java 21 and the latest Spring Boot 4.x release.
- Use PostgreSQL as the database; run it via Docker Compose.
- Use `pl.piomin.services` as the group ID for the Maven project and base Java package.
- Do not use the Lombok library.
- Generate the Docker Compose file to run all components used by the application.
- Update README.md each time you generate a new version.
- Write all code, comments, commit messages and documentation in English. The user may talk to you in Polish; reply in the user's language, but never put Polish into files.

## API contract (consumed by the Web UI)
- Controllers never expose entities: use `*Response`, `*CreateRequest`, `*UpdateRequest` records.
- Errors are RFC 9457 Problem Details; validation and unique-constraint errors add `errors: [{field, code, message}]` (see `ApiExceptionHandler`). `code` is a stable, documented enum the UI translates (`required`, `email`, `past`, `unique`, `invalid`, `date-range`, `not-found`, `in-use`); `message` is an English fallback. `field` is omitted for errors about the whole record (`in-use`). A new constraint or code must be added to the mapping, the `FieldError` schema enum, the README table and the tests.
- Cross-field rules are class-level constraints that report the error on one field, so the UI can show it next to an input: `@DateRange` (on the `AvailabilityWindow` interface) reports `availableTo` with code `date-range`.
- Error responses are documented by convention in `OpenApiConfig#problemResponses` (400/409 on POST/PUT, 400 on list GETs, 404 on `{id}` paths, 409 on DELETE of a path in `REFERENCED_PATHS`, `application/problem+json`, schema `Problem`). Add a resource to `REFERENCED_PATHS` when another resource starts referencing it. Keep new endpoints consistent with it instead of annotating them.
- List endpoints are paged (`page`, `size`, `sort`, `q`) and return `PagedModel` (`{content, page}`).
- List filters are optional, named query parameters next to `q`, `page`, `size` and `sort` (e.g. `status`, `availableOn`, `countryCode`), never a generic filter expression. They are bound to a `<Resource>Filter` record (`@ParameterObject @Valid`, with `@Parameter(description)` per component) that builds a JPA `Specification`; filters combine with AND, and an invalid value returns 400 with `errors` (field = parameter name). `q` uses `Filters.search(q, attributes...)`, which escapes the LIKE wildcards `%`, `_` and `\`. Example: `GET /api/tasks?q=&done=&priority=&assigneeId=`.
- Relations:
  - Requests reference a related record by id: `<relation>Id` (e.g. `assigneeId`), nullable. An unknown id returns 400 with a field error on `<relation>Id`, code `not-found` (`ApiExceptionHandler.notFound`).
  - Responses embed a `Reference` summary `{id, label}` (e.g. `assignee`), where `label` is the display name (`Person#getDisplayName`, "firstName lastName"), so a list never needs one request per row. Load the relation with `@EntityGraph` on the repository's `findAll(Specification, Pageable)` and `findById` (`open-in-view` is off).
  - Deleting a record that is still referenced returns 409 with a `detail` and `errors: [{code: "in-use", message}]` without `field` (`ApiExceptionHandler.inUse`); it never cascades. The foreign key is `ON DELETE RESTRICT` as a backstop.
  - A filter on a relation is `<relation>Id`.
- Method names are the OpenAPI operationIds: `list<Resource>s`, `get<Resource>`, `create<Resource>`, `update<Resource>`, `delete<Resource>`.
- Endpoints live under `/api`; reads need role `person-viewer` or `person-editor`, writes need `person-editor`.
- `PUT` is a full replacement: `*UpdateRequest` has the same constraints as `*CreateRequest` plus a required `version` from the last read (409 on mismatch). Optional fields sent as null are cleared; a field with a default (`status`, `priority`, `done`) sent as null gets its default again: the entity setter maps null to it (`Objects.requireNonNullElse`), the request documents it with `@Schema(defaultValue)`, and the migration has the same `DEFAULT`.
- Server-set fields (`createdAt`, `updatedAt` via `@CreationTimestamp`/`@UpdateTimestamp`, `Instant`) appear only in responses, marked `@Schema(accessMode = READ_ONLY)`.
- Validation constraints must translate to JSON Schema/JavaScript (the UI generates Zod schemas from the contract): no Java-only regex syntax such as `(?s)` or possessive quantifiers; prefer `@NotBlank`, `@Size`, `@Email`. A rule Bean Validation cannot express gets a custom constraint plus the matching `@Schema` keyword (e.g. a step: `@MultipleOf("0.5")` with `@Schema(multipleOf = 0.5)`), and a DB `CHECK` with the same rule.
- Response records mark always-present fields with `@Schema(requiredMode = REQUIRED)`, so the generated TypeScript types are not optional; responses are documented as `application/json` (`springdoc.default-produces-media-type`).
- After any API change run `mvn clean verify` and commit the regenerated `contract/openapi.json` (CI fails otherwise).

## Database
- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it (`ddl-auto: validate`), so any mismatch between entities and migrations fails at startup.
- Never edit a migration that has been applied; every change is one new file `V<n>__<description>.sql`.
- Every entity change ships with its migration in the same commit.
- A new NOT NULL column gets a `DEFAULT` or a backfill, so existing rows keep working.
- Name constraints and indexes explicitly (`pk_`, `uk_`, `ck_`, `fk_`, `ix_`); index every foreign key column; `uk_person_email` is referenced by `Person.UNIQUE_EMAIL` for the `unique` error code.

## Implementation notes (Spring Boot 4 / Spring Data 4 / springdoc 3)
- `PropertyReferenceException` and `TypeInformation` moved to `org.springframework.data.core` in Spring Data 4.
- Do not put `@EnableSpringDataWebSupport` on the application class: `@WebMvcTest` registers it twice and fails. Controllers return `PagedModel` explicitly instead.
- Any `@ApiResponse` on a controller method (also via a meta-annotation) stops springdoc 3 from generating the success response; that is why error responses are added by an `OpenApiCustomizer`.
- Schemas registered manually must use `ModelConverters.getInstance(true)` (OpenAPI 3.1); the default instance omits `"type": "object"`.
- Error codes come from `FieldError#getCode()` (the constraint's simple name, e.g. `NotBlank`) mapped in `ApiExceptionHandler#code`. The `Problem` record exists only to document the error body; keep it in sync with what the handler returns.
- `ApiExceptionHandler.notFound`/`inUse` throw Spring's `ErrorResponseException` with a prepared `ProblemDetail`; `ResponseEntityExceptionHandler` renders it, so no extra `@ExceptionHandler` is needed.
- `@WebMvcTest(XController.class)` creates no repositories: a repository newly injected into an existing controller (e.g. `TaskRepository` in `PersonController`) needs a `@MockitoBean` in that controller's test.
- `exportOpenApiContract` writes the contract first and asserts afterwards, so a failing assertion still leaves the generated file to inspect.
- Annotate `Pageable` parameters with springdoc's `@ParameterObject`, otherwise the contract shows one `pageable` object instead of `page`/`size`/`sort`.
- The OpenAPI `info.version` comes from Maven `build-info` (`BuildProperties`); never hard-code it.
- Keycloak realm roles are read from the `realm_access.roles` claim and mapped to `ROLE_<role>` in `SecurityConfig`.
- The Keycloak realm (`k8s/realm.json`) is shared by Docker Compose and Kubernetes; `--import-realm` skips an existing realm, so after changing it run `docker compose up -d --force-recreate keycloak` (Kubernetes rolls a new pod automatically via the ConfigMap hash). The realm has i18n enabled (`en` default, `pl`); the login page follows `ui_locales`.
- `@NotBlank` appears in the contract only as `minLength: 1`, so a whitespace-only value passes client-side validation and is rejected by the server with a field error.
- Run integration tests with Podman by setting `DOCKER_HOST` to the Podman socket and `TESTCONTAINERS_RYUK_DISABLED=true` (see README). Without Docker/Podman they are skipped, and the contract is not regenerated.
- Ports 5432/8080 may be taken by containers of a sibling project (`spring-person-api-*`); check `podman ps` before `docker compose up`. Stop them only when the user asks, and then with `podman stop` (not `compose down`), so their data survives.

## Workflow
- Build with `mvn clean verify`, not plain `mvn verify`: classes compiled by the IDE (Eclipse compiler) in `target` can hide `javac` errors such as a duplicate method.
- macOS ships BSD tools: `sed` has no `\|` alternation and `grep` has no `-P`; use `perl -pi -e` for regex edits, and the Edit tool for multi-line changes.
- Commit only when asked, and push only when asked; "commit" alone does not mean push.
- Before committing, scan changed files for Polish text (all files must be in English).
- After a push, check CI: `gh api repos/mmartofel/spring-person-api-claude/commits/<sha>/status` (context `ci/circleci: build`; logs at the `target_url`). Poll every 30 s or slower.
  - The owner is `mmartofel` (with an `l`; the local user is `mmartofe`). Take it from `git remote get-url origin` instead of typing it: a misspelled owner gives a 404 that looks like an access or GitHub problem.
  - Without `gh`, the repo is public: `curl -s https://api.github.com/repos/mmartofel/spring-person-api-claude/commits/<sha>/status | jq -r .state` (60 requests per hour).
- After a version bump, rebuild and restart the locally running API (`java -jar target/spring-person-api-<version>.jar`), and recreate Keycloak if `k8s/realm.json` changed.
