# Example Person API vibe coded with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes

This is a simple REST API for managing `Person` and `Task` records, with OAuth2 JWT authentication via Keycloak. It uses Spring Boot 4, PostgreSQL, and can be deployed to Kubernetes using Skaffold.
Purpose is to demonstrate a modern Java stack with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes deployment.

Spring Boot Plugin for Claude Code has been used to vibe code in this project. You can find the plugin here:

https://github.com/piomin/claude-ai-spring-boot

Kudos to Piotr Minkowski, the great plugin author, and to Claude AI for the amazing AI coding assistance.

Version: **3.6.0**

## Changelog
- **3.6.0**: Deployment follows [docs/DEPLOY-CONVENTION.md](docs/DEPLOY-CONVENTION.md), shared with the UI repo: `deploy/base` plus overlays `local`, `openshift` and `kubernetes`, with the same Skaffold profiles. Nothing in git names a cluster, host, registry or namespace, and no secret value is in the manifests: `deploy/ensure-secrets.sh` (a Skaffold before-deploy hook) creates the missing Secrets with random values. Keycloak has no Route of its own: browsers reach the realm through the UI's host, and the `person-ui` client accepts any host it is served on (`rootUrl ${authBaseUrl}`, relative redirects). Topology labels, resources and arrows are in the manifests. Migration: `k8s/realm.json` moved to `deploy/base/realm.json`; client secret and demo passwords are `${ENV}` placeholders (Docker Compose sets the old demo values). The API and contract are unchanged.
- **3.5.2**: Every component has health checks. In Kubernetes and OpenShift, PostgreSQL (`pg_isready`) and Keycloak (`/health/started`, `/health/ready`, `/health/live` on port 9000) have startup, readiness and liveness probes, like the API. In Docker Compose, both services have a healthcheck, and `docker compose up -d --wait` returns once they are usable. The API and contract are unchanged.
- **3.5.1**: Fix: in Kubernetes and OpenShift, PostgreSQL keeps its data in a PersistentVolumeClaim (`postgres`, 1Gi). Before, every `skaffold run` replaced the pod and emptied the database. The API has a startup probe, so a slow start is not killed and does not log probe failures. Keycloak has a readiness probe (`KC_HEALTH_ENABLED`, `/health/ready` on the management port 9000), so it gets traffic only after it has started and imported the realm; before, token requests failed for a few seconds after a redeploy. The API and contract are unchanged.
- **3.5.0**: OpenShift deployment: Skaffold profile `openshift` with a Kustomize overlay (`openshift/`) that exposes the API and Keycloak through TLS edge Routes. See [Deploy to OpenShift](#deploy-to-kubernetes-and-openshift). The API and contract are unchanged.
- **3.4.3**: Fix: `/actuator/info` (with `build.version`) is public, and `/actuator/health` lists its components (`db`, `diskSpace`, ...) without details, for the UI footer. Other Actuator endpoints stay protected. The readiness probe now checks the database (its group setting was misplaced and ignored). The contract is unchanged.
- **3.4.2**: Fix: the contract now marks a task `title` as `minLength: 1`, so the UI rejects an empty title before sending it (it showed `minLength: 0`, because `@Size(max)` overrode `@NotBlank`). An empty required text returns one `required` error, not also `invalid`. No behaviour change otherwise.
- **3.4.1**: Fix: `PUT /api/tasks/{id}` with a new `assigneeId` saved the change but returned 500. Task writes now run in a transaction. Unexpected errors return Problem Details (500) instead of Spring's default error body. The contract is unchanged.
- **3.4.0**: New `Task` resource at `/api/tasks` with an optional assignee (a person).
  - Requests reference the assignee as `assigneeId`. Responses embed `assignee: {id, label}`.
  - An unknown `assigneeId` returns 400 with code `not-found`.
  - Deleting a person who still has tasks returns 409 with code `in-use`.
  - `FieldError.field` is now optional in the contract, because `in-use` is about the whole record.
- **3.3.0**: A person has an optional `address` (`street`, `postalCode`, `city`, `countryCode`), a `status` (`AVAILABLE` by default, `UNAVAILABLE`) and an optional availability window (`availableFrom`, `availableTo`). A reversed window returns the error code `date-range`. The list endpoint filters by `status`, `availableOn` and `countryCode`, and documents its 400 response. Existing rows get `status` `AVAILABLE` (migration `V2`).
- **3.2.0**: Flyway migrations own the database schema; Hibernate only validates it. Databases created by Hibernate before 3.2.0 must be recreated (see [Database migrations](#database-migrations)). The API is unchanged.
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

A Spring Boot 4 REST API for managing `Person` and `Task` records, stored in PostgreSQL. Every endpoint requires an OAuth2 JWT issued by Keycloak. Kubernetes deployment uses Skaffold.

## Stack
- Java 21, Spring Boot 4.1.1, Maven
- Spring Data JPA + PostgreSQL 18, Flyway migrations
- Spring Security OAuth2 Resource Server (JWT, validated against Keycloak's JWK set)
- Keycloak 26.8.1, realm `person`: confidential client `person-api`, public client `person-ui` (Authorization Code + PKCE)
- springdoc-openapi 3.1.1 (OpenAPI contract + Swagger UI)
- Jib + Skaffold + Kustomize for Kubernetes and OpenShift
- JUnit 5, MockMvc, Testcontainers; CircleCI

## API
All `/api/**` endpoints require `Authorization: Bearer <token>`. Reads need realm role `person-viewer` or `person-editor`; writes need `person-editor`. Public: `/actuator/health/**` (status of each component, no details), `/actuator/info` (`build.version`), `/v3/api-docs`, `/swagger-ui.html`.

### Persons

| Method | Path                | Result                                                       |
|--------|---------------------|--------------------------------------------------------------|
| GET    | `/api/persons`      | 200, page `{content, page:{size, number, totalElements, totalPages}}`; 400 on an invalid filter or sort |
| GET    | `/api/persons/{id}` | 200, or 404                                                  |
| POST   | `/api/persons`      | 201, 400 on invalid body, 409 if the email is taken          |
| PUT    | `/api/persons/{id}` | 200, 400, 404, or 409 (email taken or stale `version`)       |
| DELETE | `/api/persons/{id}` | 204, 404, or 409 if the person still has tasks (`in-use`)    |

The list endpoint takes these query parameters:
- `page` (from 0) and `size` (default 20, max 100).
- `sort`, e.g. `sort=lastName,desc`. An unknown property returns 400.
- `q`: case-insensitive search in first name, last name and email.
- `status`: `AVAILABLE` or `UNAVAILABLE`.
- `availableOn` (ISO date): persons with status `AVAILABLE` whose availability window contains the date. A missing `availableFrom` or `availableTo` means no limit on that side.
- `countryCode`: the address country, e.g. `PL`.

Filters combine with AND. An invalid value (unknown status, malformed date, lowercase country code) returns 400 with the parameter in `errors`.

`PUT` is a full replacement. The body has the same fields as `POST`, plus the `version` from the last read. Optional fields (`birthDate`, `gender`, `address`, `availableFrom`, `availableTo`) sent as `null` or left out are cleared, and a missing `status` becomes `AVAILABLE`. If someone changed the record in the meantime, it returns 409.

Fields:

| Field | Type and rules |
|-------|----------------|
| `id`, `version` | Read-only (`version` is required in `PUT`) |
| `firstName`, `lastName` | Required, not blank |
| `email` | Required, well-formed, unique |
| `birthDate` | Optional ISO date in the past |
| `gender` | Optional: `MALE`, `FEMALE`, `OTHER` |
| `address` | Optional object, every part optional: `street` (max 120), `postalCode` (max 16, free text), `city` (max 80), `countryCode` (ISO 3166-1 alpha-2, `^[A-Z]{2}$`). An address with all parts empty is returned as `null`. |
| `status` | `AVAILABLE` or `UNAVAILABLE`; defaults to `AVAILABLE` when not sent; always present in responses |
| `availableFrom`, `availableTo` | Optional ISO dates: the window in which the person can be booked. An empty end means no limit; `availableFrom` must not be after `availableTo`. |

A person who is the assignee of any task cannot be deleted. The response is 409, the `detail` says how many tasks, and `errors` is `[{"code":"in-use","message":"still referenced by tasks"}]` (no `field`). Reassign or delete the tasks first.

### Tasks

| Method | Path              | Result                                                         |
|--------|-------------------|----------------------------------------------------------------|
| GET    | `/api/tasks`      | 200, page `{content, page}`; 400 on an invalid filter or sort  |
| GET    | `/api/tasks/{id}` | 200, or 404                                                    |
| POST   | `/api/tasks`      | 201, 400 (invalid body or unknown `assigneeId`)                |
| PUT    | `/api/tasks/{id}` | 200, 400, 404, or 409 (stale `version`)                        |
| DELETE | `/api/tasks/{id}` | 204, or 404                                                    |

The list endpoint takes `page`, `size`, `sort` and these filters, combined with AND:
- `q`: case-insensitive search in title and description.
- `done`: `true` or `false`.
- `priority`: `LOW`, `MEDIUM` or `HIGH`.
- `assigneeId`: tasks assigned to this person.

`PUT` is a full replacement with the `version` from the last read. Optional fields sent as `null` or left out are cleared (`assigneeId: null` unassigns the task). A missing `priority` becomes `MEDIUM` and a missing `done` becomes `false`.

| Field | Type and rules |
|-------|----------------|
| `id`, `version` | Read-only (`version` is required in `PUT`) |
| `title` | Required, not blank, max 120 |
| `description` | Optional long text, max 2000 |
| `priority` | `LOW`, `MEDIUM`, `HIGH`; defaults to `MEDIUM` when not sent |
| `estimateHours` | Optional number from 0.5 to 999 in steps of 0.5 |
| `done` | Boolean; defaults to `false` when not sent |
| `dueDate` | Optional ISO date (past or future) |
| `assigneeId` (request) | Optional id of a person; an unknown id returns 400 `not-found` on `assigneeId` |
| `assignee` (response) | `{id, label}`, where `label` is "firstName lastName"; `null` when unassigned |
| `createdAt`, `updatedAt` | Read-only timestamps set by the server (ISO 8601, UTC) |

### Errors

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
| `date-range` | `availableTo` is before `availableFrom` (reported on `availableTo`) | `@DateRange` |
| `not-found` | The referenced record does not exist, e.g. `assigneeId` (400) | lookup of the related record |
| `in-use`   | The record is still referenced and cannot be deleted (409, no `field`) | delete of an assigned person |
| `invalid`  | Any other constraint, e.g. too long, wrong country code, estimate not in steps of 0.5, malformed filter value | `@Size`, `@Pattern`, `@DecimalMin`, `@DecimalMax`, `@MultipleOf`, type conversion and other constraints |

Which error responses each endpoint can return:
- 400 on `POST` and `PUT`, and on the list endpoint (invalid filter or sort).
- Nested fields are reported with a path, e.g. `address.countryCode`.
- 404 on every `/{id}` path.
- 409 on `POST` and `PUT`: a duplicate email has `errors`, a stale `version` has none.
- 409 on `DELETE /api/persons/{id}` while the person has tasks (`in-use`).
- 500 on an unexpected server error, as Problem Details with a generic `detail` (the cause is only logged). It is not listed in the contract.

### OpenAPI contract
The contract for the Web UI is committed in `contract/openapi.json`. `mvn clean verify` regenerates it (in `PersonApplicationIT`), and CI fails if the committed file is out of date. While the app runs, Swagger UI is at http://localhost:8081/swagger-ui.html: click **Authorize** and paste a token.

## Run locally
```shell
docker compose up -d --wait   # PostgreSQL on 5432, Keycloak on 8080; waits until both are healthy
mvn spring-boot:run           # API on 8081

TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=person-api -d client_secret=person-api-secret \
  http://localhost:8080/realms/person/protocol/openid-connect/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Smith","email":"john@example.com","gender":"MALE",
       "address":{"city":"Warsaw","countryCode":"PL"},"availableFrom":"2026-11-01","availableTo":"2026-11-30"}' \
  http://localhost:8081/api/persons
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/persons?q=smi&sort=lastName"
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/persons?availableOn=2026-11-15&countryCode=PL"

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Prepare the offer","priority":"HIGH","estimateHours":2.5,"assigneeId":1}' \
  http://localhost:8081/api/tasks
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/tasks?done=false&assigneeId=1"
```
The `person-api` service account has the `person-editor` role.

For the browser UI, the realm also has a public client, `person-ui`:
- Flow: Authorization Code + PKCE.
- Redirect URIs: `http://localhost:5173/*` (the UI's dev server) and `/*` relative to `rootUrl ${authBaseUrl}`, so the
  host the realm is served on (the UI's host in a cluster). Web origins `+`, the origins of the redirect URIs.
- Demo users: `viewer` with role `person-viewer`, `editor` with role `person-editor`. Their passwords, and the
  `person-api` client secret, are `${ENV}` placeholders in the realm. Docker Compose sets them to `viewer`, `editor` and
  `person-api-secret`; a cluster takes them from Secrets (Deploy).

The login page follows the UI language: pass `ui_locales=pl` (or `en`, the default) in the authorization request.

### Applying changes to the Keycloak realm
Keycloak imports `deploy/base/realm.json` with `--import-realm`, which **skips a realm that already exists**. Editing the file does not change a running Keycloak:
- **Docker Compose:** run `docker compose up -d --force-recreate keycloak`. Keycloak in `start-dev` keeps its data inside the container, so the new container imports the realm from scratch. Users and settings added in the Admin Console are lost.
- **Kubernetes / Skaffold:** Kustomize's `configMapGenerator` adds a content hash to the ConfigMap name. A changed `realm.json` therefore rolls out a new Keycloak pod, which imports the realm again. `skaffold dev` does this on save.

### Database migrations
Flyway creates and upgrades the schema at startup from `src/main/resources/db/migration` (`V<n>__<description>.sql`). Hibernate runs with `ddl-auto: validate`, so the app does not start if the entities and the migrations disagree.

Databases created by Hibernate before 3.2.0 have no Flyway history, and Flyway refuses to migrate a non-empty schema without one. Recreate them (this deletes the data):
- **Docker Compose:** `docker compose down -v && docker compose up -d`. The `-v` removes the PostgreSQL volume.
- **Kubernetes / OpenShift:** the data lives in the PersistentVolumeClaim `postgres` and survives redeploys. To start with an empty database, run `kubectl scale deploy/postgres --replicas=0`, then `kubectl delete pvc postgres`, then `skaffold run` again with the same profile.

### Fake data for UI testing
`dev/seed-fake-data.sql` adds 250 persons and 2000 tasks (about 10% unassigned) to check how a UI handles larger lists. It always generates the same data, adds it next to existing rows, and adds another full set each time it runs. Run it after the app has started once, so Flyway has created the schema:
```shell
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U persons -d persons < dev/seed-fake-data.sql
kubectl exec -i deploy/postgres -- sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < dev/seed-fake-data.sql
```
Seed persons have e-mails at `seed.example.com` and seed task titles end with ` #seed`. To remove them, run in `psql`:
```sql
DELETE FROM task WHERE title LIKE '% #seed';
DELETE FROM person WHERE email LIKE '%@seed.example.com';
```

## Connect a UI
A web UI made from [crud-ui-blueprint](https://github.com/mmartofel/crud-ui-blueprint) reads this section in its
`crud-ui-init` skill. Its `.env.local` for this API's local stack (Keycloak 8080, API 8081, realm `person`):
```
VITE_OIDC_AUTHORITY=http://localhost:8080/realms/person
VITE_OIDC_CLIENT_ID=person-ui
VITE_OIDC_ROLES_CLAIM=realm_access.roles
API_PROXY_TARGET=http://localhost:8081
CONTRACT_SOURCE=../spring-person-api-claude/contract/openapi.json
E2E_WRITER_USER=editor
E2E_WRITER_PASSWORD=editor
E2E_READER_USER=viewer
E2E_READER_PASSWORD=viewer
```
Read role `person-viewer`, write role `person-editor` (realm roles, in `realm_access.roles` of the access token). In a
cluster, the UI's defaults fit this stack in the same namespace: `API_UPSTREAM=http://spring-person-api:8080`,
`OIDC_UPSTREAM=http://keycloak:8080` and `OIDC_AUTHORITY=/realms/person`. Its nginx serves the realm on the UI's
host, so the browser needs no Keycloak host (see Deploy). The UI footer reads the public
`/actuator/health` (components) and `/actuator/info` (`build.version`) without a token.

## Deploy to Kubernetes and OpenShift
The API repo and the UI repo ([person-ui-v2](https://github.com/mmartofel/person-ui-v2)) deploy the same way: Skaffold
with kustomize overlays, by [docs/DEPLOY-CONVENTION.md](docs/DEPLOY-CONVENTION.md). Nothing in git names a cluster,
host, registry or namespace. Install this repo first, then the UI from its repo, into the same namespace:
```shell
skaffold run -p openshift --default-repo=quay.io/<org> -n <namespace>   # PostgreSQL, Keycloak, API (or -p kubernetes)
```
Then, in person-ui-v2, the same line installs the UI. Sign-in is on the UI's Route or Ingress host.

| Path | What |
|---|---|
| `deploy/base/` | PostgreSQL (PVC `postgres`, 1Gi), Keycloak (`start-dev`, realm `realm.json`), the API and its ConfigMap `spring-person-api-config` (`DB_HOST`, `DB_PORT`, `KEYCLOAK_URL`, `REALM`); probes, resources, `app.kubernetes.io/*` labels |
| `deploy/overlays/local/` | default profile, a local cluster: API forwarded to `localhost:8081`, Keycloak to `localhost:8080` |
| `deploy/overlays/openshift/` | a Route for the API (Swagger UI, machine clients; generated host) and the Topology view's icons and arrows |
| `deploy/overlays/kubernetes/` | the base as is: the stack stays internal, the UI's Ingress is its public entry |
| `deploy/environments/<env>/` | git-ignored, see `example/`: one environment's hosts or settings, `DEPLOY_OVERLAY=deploy/environments/<env> skaffold run -p ...` |
| `deploy/ensure-secrets.sh` | Skaffold's before-deploy hook: creates the missing Secrets |

- **Secrets.** Before each deploy, `deploy/ensure-secrets.sh` creates any of these that is missing in the namespace,
  with random values: `postgres` (database, username, password), `keycloak-admin`, `spring-person-api-oidc-client`
  (the `person-api` client secret) and `spring-person-api-demo-users` (passwords of `viewer` and `editor`). It never
  changes an existing Secret, because PostgreSQL keeps the password of its first start. Keycloak takes the client
  secret and the demo passwords into the realm through `${ENV}` placeholders. To choose the test users' passwords,
  set them on the first deploy: `DEMO_EDITOR_PASSWORD=... DEMO_VIEWER_PASSWORD=... skaffold run ...`. To read them:
  `kubectl get secret spring-person-api-demo-users -o jsonpath='{.data.editor-password}' | base64 -d`.
- **Keycloak.** It runs with `KC_PROXY_HEADERS=xforwarded` and no fixed hostname, and has no Route: browsers reach
  `/realms/person` through the UI's host, whose nginx proxies it. The `person-ui` client has `rootUrl ${authBaseUrl}`
  and relative redirect URIs, so it accepts the host it is served on, whatever the cluster gives it. Tokens issued
  there carry `iss=https://<ui host>/realms/person`. The API checks them against the JWK set inside the cluster.
- **Image.** Jib builds and pushes it straight to `--default-repo`, with no Docker or Podman. It reads registry
  credentials from `~/.docker/config.json`, not from Podman's `auth.json`. With a Quay.io robot account:
  ```shell
  echo "$QUAY_TOKEN" | podman login quay.io -u "$QUAY_USER" --password-stdin --authfile ~/.docker/config.json
  ```
  - `--default-repo` is the registry path `quay.io/<namespace>`, not the web page `quay.io/repository/<namespace>`.
  - A robot account usually cannot create a repository: create `spring-person-api` in Quay first, and make it public or
    give the namespace a pull secret, otherwise the pod stays in `ImagePullBackOff`.
  - Skaffold's warning `Could not get docker context` is harmless.
- **Topology.** In the OpenShift console, the stack is the `person-app` group: Spring, PostgreSQL and Keycloak icons,
  arrows from the API to PostgreSQL and Keycloak, and a link to the source. Add the version label with
  `--label app.kubernetes.io/version=$(git rev-parse --short HEAD)`.
- `skaffold dev` deploys to a local cluster and forwards the ports, so the curl commands above work unchanged.
- `skaffold delete -p openshift -n <namespace>` removes what this repo installed, including the database volume. The
  Secrets stay; delete them with `kubectl delete secret -l app.kubernetes.io/part-of=person-app`.

A machine-client token through the API's Route (Keycloak in the cluster, reached with a port-forward):
```shell
API=https://$(oc get route spring-person-api -o jsonpath='{.spec.host}')
SECRET=$(oc get secret spring-person-api-oidc-client -o jsonpath='{.data.client-secret}' | base64 -d)
oc port-forward svc/keycloak 18080:8080 >/dev/null & sleep 2
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=person-api -d client_secret=$SECRET \
  http://localhost:18080/realms/person/protocol/openid-connect/token | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" "$API/api/persons"
```
Swagger UI is at `$API/swagger-ui.html`, with `https` server URLs.

## Tests
```shell
mvn test                                      # unit tests (MockMvc)
mvn clean verify                              # plus Testcontainers integration tests (needs Docker)
mvn test -Dtest=PersonControllerTest#createPersonInvalid   # single test
mvn clean verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=PersonApplicationIT#taskAssigneeChanges        # single integration test
```
With Podman instead of Docker, point Testcontainers at the Podman socket:
```shell
DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') \
TESTCONTAINERS_RYUK_DISABLED=true mvn clean verify
```
The local stack also runs with `podman compose up -d`.

The integration tests are skipped automatically when Docker is not available. CircleCI (`.circleci/config.yml`) runs `mvn verify` on a machine executor with Docker, then checks that `contract/openapi.json` is committed. The result appears on GitHub next to each commit (✓ or ✗); **Details** opens the CircleCI log.
