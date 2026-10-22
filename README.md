# Example Person API vibe coded with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes

This is a simple REST API for managing `Person` records, with OAuth2 JWT authentication via Keycloak. It uses Spring Boot 4, PostgreSQL, and can be deployed to Kubernetes using Skaffold.
Purpose is to demonstrate a modern Java stack with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes deployment.

Spring Boot Plugin for Claude Code has been used to vibe code in this project. You can find the plugin here:

https://github.com/piomin/claude-ai-spring-boot

Kudos to Piotr Minkowski, the great plugin author, and to Claude AI for the amazing AI coding assistance.

Version: **3.1.1**

## Changelog
- **3.1.1**: The contract marks `status` and `title` of `Problem` as required (Spring always sets them).
- **3.1.0**: Each item in a Problem Details `errors[]` has a stable `code` (`required`, `email`, `past`, `unique`, `invalid`) that the UI can translate. The contract documents the error responses (`Problem` and `FieldError` schemas, `application/problem+json`).
- **3.0.0** (breaking): `PUT /api/persons/{id}` is a full replacement.
  - The body needs all required fields plus `version`.
  - Optional fields sent as `null` (`birthDate`, `gender`) are cleared.
  - The contract documents responses as `application/json`, marks always-present `PersonResponse` fields as required, and contains no Java-only regex.
  - The Keycloak login page is available in English and Polish.
- **2.0.0** (breaking): API is ready for a generated Web UI.
  - Endpoints moved to `/api/persons`.
  - The list endpoint is paged and searchable.
  - Requests and responses use DTOs; the entity is no longer exposed.
  - `PUT` requires `version` (optimistic locking, 409 on conflict).
  - Email is unique (409).
  - Errors are Problem Details with per-field `errors`.
  - Roles `person-viewer` and `person-editor`.
  - OpenAPI contract in `contract/openapi.json` and Swagger UI.
  - Public Keycloak client `person-ui` (PKCE) with demo users.
- **1.0.1**: `PUT /persons/{id}` keeps fields that aren't sent instead of clearing them.
- **1.0.0**: First version.

A Spring Boot 4 REST API for managing `Person` records, stored in PostgreSQL. Every endpoint requires an OAuth2 JWT issued by Keycloak. Kubernetes deployment uses Skaffold.

## Stack
- Java 21, Spring Boot 4.1.1, Maven
- Spring Data JPA + PostgreSQL 18
- Spring Security OAuth2 Resource Server (JWT, validated against Keycloak's JWK set)
- Keycloak 26.8.1, realm `person`: confidential client `person-api`, public client `person-ui` (Authorization Code + PKCE)
- springdoc-openapi 3.1.1 (OpenAPI contract + Swagger UI)
- Jib + Skaffold + Kustomize for Kubernetes
- JUnit 5, MockMvc, Testcontainers; CircleCI

## API
All `/api/**` endpoints require `Authorization: Bearer <token>`. Reads need realm role `person-viewer` or `person-editor`; writes need `person-editor`. Public: `/actuator/health/**`, `/v3/api-docs`, `/swagger-ui.html`.

| Method | Path                | Result                                                       |
|--------|---------------------|--------------------------------------------------------------|
| GET    | `/api/persons`      | 200, page `{content, page:{size, number, totalElements, totalPages}}` |
| GET    | `/api/persons/{id}` | 200, or 404                                                  |
| POST   | `/api/persons`      | 201, 400 on invalid body, 409 if the email is taken          |
| PUT    | `/api/persons/{id}` | 200, 400, 404, or 409 (email taken or stale `version`)       |
| DELETE | `/api/persons/{id}` | 204, or 404                                                  |

The list endpoint takes these query parameters:
- `page` (from 0) and `size` (default 20, max 100).
- `sort`, e.g. `sort=lastName,desc`. An unknown property returns 400.
- `q`: case-insensitive search in first name, last name and email.

`PUT` is a full replacement. The body has the same fields as `POST`, plus the `version` from the last read. Optional fields (`birthDate`, `gender`) sent as `null` or left out are cleared. If someone changed the record in the meantime, it returns 409.

Response fields: `id`, `firstName`, `lastName`, `email`, `birthDate` (ISO date, past), `gender` (`MALE`, `FEMALE`, `OTHER`), `version`.

Errors are [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`, schema `Problem` in the contract). Validation and unique-email errors list the affected fields in `errors`:
```json
{"status":400,"title":"Bad Request","detail":"Invalid request content.","instance":"/api/persons",
 "errors":[{"field":"email","code":"email","message":"must be a well-formed email address"}]}
```
`code` is stable, so the UI should translate it. `message` is an English fallback.

| `code`     | Meaning                                         | Source                     |
|------------|-------------------------------------------------|----------------------------|
| `required` | The field is missing or blank                   | `@NotBlank`, `@NotNull`    |
| `email`    | Not a well-formed email address                 | `@Email`                   |
| `past`     | The date must be in the past                    | `@Past`                    |
| `unique`   | The value is already used by another person (409) | unique email constraint  |
| `invalid`  | Any other constraint                            | other Bean Validation constraints |

Which error responses each endpoint can return:
- 400 on `POST` and `PUT`.
- 404 on every `/{id}` path.
- 409 on `POST` and `PUT`: a duplicate email has `errors`, a stale `version` has none.

### OpenAPI contract
The contract for the Web UI is committed in `contract/openapi.json`. `mvn verify` regenerates it (in `PersonApplicationIT`), and CI fails if the committed file is out of date. While the app runs, Swagger UI is at http://localhost:8081/swagger-ui.html: click **Authorize** and paste a token.

## Run locally
```shell
docker compose up -d          # PostgreSQL on 5432, Keycloak on 8080
mvn spring-boot:run           # API on 8081

TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=person-api -d client_secret=person-api-secret \
  http://localhost:8080/realms/person/protocol/openid-connect/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Smith","email":"john@example.com","gender":"MALE"}' \
  http://localhost:8081/api/persons
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/persons?q=smi&sort=lastName"
```
The `person-api` service account has the `person-editor` role.

For the browser UI, the realm also has a public client, `person-ui`:
- Flow: Authorization Code + PKCE.
- Redirect URI and web origin: `http://localhost:5173`.
- Demo users (username/password): `viewer`/`viewer` with role `person-viewer`, `editor`/`editor` with role `person-editor`.

The login page follows the UI language: pass `ui_locales=pl` (or `en`, the default) in the authorization request.

### Applying changes to the Keycloak realm
Keycloak imports `k8s/realm.json` with `--import-realm`, which **skips a realm that already exists**. Editing the file does not change a running Keycloak:
- **Docker Compose:** run `docker compose up -d --force-recreate keycloak`. Keycloak in `start-dev` keeps its data inside the container, so the new container imports the realm from scratch. Users and settings added in the Admin Console are lost.
- **Kubernetes / Skaffold:** Kustomize's `configMapGenerator` adds a content hash to the ConfigMap name. A changed `realm.json` therefore rolls out a new Keycloak pod, which imports the realm again. `skaffold dev` does this on save.

Upgrading a local database from 1.x: 2.0.0 adds a `version` column and a unique email constraint. Recreate the containers with `docker compose down && docker compose up -d`.

## Deploy to Kubernetes
```shell
skaffold dev                                  # local cluster, e.g. minikube or kind
skaffold run --default-repo=<your-registry>   # remote cluster
```
Jib builds the image without a Docker daemon. The manifests in `k8s/` (PostgreSQL, Keycloak, the app) go through Kustomize. Skaffold forwards the API to `localhost:8081` and Keycloak to `localhost:8080`, so the curl commands above work unchanged.

The Keycloak realm is defined once, in `k8s/realm.json`, and used by both Docker Compose and Kubernetes. The client secret, demo user passwords and DB credentials are demo values; replace them for any real environment.

## Tests
```shell
mvn test                                      # unit tests (MockMvc)
mvn verify                                    # plus Testcontainers integration tests (needs Docker)
mvn test -Dtest=PersonControllerTest#createPersonInvalid   # single test
```
With Podman instead of Docker, point Testcontainers at the Podman socket:
```shell
DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') \
TESTCONTAINERS_RYUK_DISABLED=true mvn verify
```
The local stack also runs with `podman compose up -d`.

The integration tests are skipped automatically when Docker is not available. CircleCI (`.circleci/config.yml`) runs `mvn verify` on a machine executor with Docker, then checks that `contract/openapi.json` is committed. The result appears on GitHub next to each commit (✓ or ✗); **Details** opens the CircleCI log.
