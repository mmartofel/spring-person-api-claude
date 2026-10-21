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
- Errors are RFC 9457 Problem Details; validation errors add `errors: [{field, message}]` (see `ApiExceptionHandler`).
- List endpoints are paged (`page`, `size`, `sort`, `q`) and return `PagedModel` (`{content, page}`).
- Method names are the OpenAPI operationIds: `list<Resource>s`, `get<Resource>`, `create<Resource>`, `update<Resource>`, `delete<Resource>`.
- Endpoints live under `/api`; reads need role `person-viewer` or `person-editor`, writes need `person-editor`.
- `PUT` is a partial update: null fields keep stored values, and the request must carry the `version` from the last read (409 on mismatch).
- After any API change run `mvn verify` and commit the regenerated `contract/openapi.json` (CI fails otherwise).

## Implementation notes (Spring Boot 4 / Spring Data 4 / springdoc 3)
- `PropertyReferenceException` and `TypeInformation` moved to `org.springframework.data.core` in Spring Data 4.
- Do not put `@EnableSpringDataWebSupport` on the application class: `@WebMvcTest` registers it twice and fails. Controllers return `PagedModel` explicitly instead.
- Annotate `Pageable` parameters with springdoc's `@ParameterObject`, otherwise the contract shows one `pageable` object instead of `page`/`size`/`sort`.
- The OpenAPI `info.version` comes from Maven `build-info` (`BuildProperties`); never hard-code it.
- Keycloak realm roles are read from the `realm_access.roles` claim and mapped to `ROLE_<role>` in `SecurityConfig`.
- The Keycloak realm (`k8s/realm.json`) is shared by Docker Compose and Kubernetes; Keycloak imports it only on the first start, so recreate the container after changing it.
- Run integration tests with Podman by setting `DOCKER_HOST` to the Podman socket and `TESTCONTAINERS_RYUK_DISABLED=true` (see README). Without Docker/Podman they are skipped, and the contract is not regenerated.
- Ports 5432/8080 may be taken by containers of a sibling project; check `podman ps` before `docker compose up` and never stop containers you did not start.
