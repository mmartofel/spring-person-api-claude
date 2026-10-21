# Example Person API vibe coded with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes

This is a simple REST API for managing `Person` records, with OAuth2 JWT authentication via Keycloak. It uses Spring Boot 4, PostgreSQL, and can be deployed to Kubernetes using Skaffold.
Purpose is to demonstrate a modern Java stack with Spring Boot 4, Keycloak, PostgreSQL, and Kubernetes deployment.

Spring Boot Plugin for Claude Code has been used to vibe code in this project. You can find the plugin here:

https://github.com/piomin/claude-ai-spring-boot

Kudos to Piotr Minkowski, the great plugin author, and to Claude AI for the amazing AI coding assistance.

Version: **1.0.1**

## Changelog
- **1.0.1**: `PUT /persons/{id}` keeps fields that aren't sent instead of clearing them.
- **1.0.0**: First version.

A Spring Boot 4 REST API for managing `Person` records, stored in PostgreSQL. Every endpoint requires an OAuth2 JWT issued by Keycloak. Kubernetes deployment uses Skaffold.

## Stack
- Java 21, Spring Boot 4.1.1, Maven
- Spring Data JPA + PostgreSQL 18
- Spring Security OAuth2 Resource Server (JWT, validated against Keycloak's JWK set)
- Keycloak 26.8.1, realm `person`, confidential client `person-api`
- Jib + Skaffold + Kustomize for Kubernetes
- JUnit 5, MockMvc, Testcontainers; CircleCI

## API
All endpoints require `Authorization: Bearer <token>`. Only `/actuator/health/**` is public.

| Method | Path            | Result                                  |
|--------|-----------------|-----------------------------------------|
| GET    | `/persons`      | 200, list                               |
| GET    | `/persons/{id}` | 200, or 404                             |
| POST   | `/persons`      | 201, or 400 on invalid body             |
| PUT    | `/persons/{id}` | 200, 400 if the result is invalid, or 404 |
| DELETE | `/persons/{id}` | 204, or 404                             |

`PUT` is a partial update: fields left out of the body (or sent as `null`) keep their stored values. The merged record is validated, so an empty `firstName` still returns 400.

`Person` fields: `id`, `firstName`, `lastName`, `email`, `birthDate` (ISO date, past), `gender` (`MALE`, `FEMALE`, `OTHER`).

## Run locally
```shell
docker compose up -d          # PostgreSQL on 5432, Keycloak on 8080
mvn spring-boot:run           # API on 8081

TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=person-api -d client_secret=person-api-secret \
  http://localhost:8080/realms/person/protocol/openid-connect/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Smith","email":"john@example.com","gender":"MALE"}' \
  http://localhost:8081/persons
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/persons
```

## Deploy to Kubernetes
```shell
skaffold dev                                  # local cluster, e.g. minikube or kind
skaffold run --default-repo=<your-registry>   # remote cluster
```
Jib builds the image without a Docker daemon. The manifests in `k8s/` (PostgreSQL, Keycloak, the app) go through Kustomize. Skaffold forwards the API to `localhost:8081` and Keycloak to `localhost:8080`, so the curl commands above work unchanged.

The Keycloak realm is defined once, in `k8s/realm.json`, and used by both Docker Compose and Kubernetes. The client secret and DB credentials are demo values; replace them for any real environment.

## Tests
```shell
mvn test                                      # unit tests (MockMvc)
mvn verify                                    # plus Testcontainers integration tests (needs Docker)
mvn test -Dtest=PersonControllerTest#createInvalid   # single test
```
With Podman instead of Docker, point Testcontainers at the Podman socket:
```shell
DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') \
TESTCONTAINERS_RYUK_DISABLED=true mvn verify
```
The local stack also runs with `podman compose up -d`.

The integration tests are skipped automatically when Docker is not available. CircleCI (`.circleci/config.yml`) runs `mvn verify` on a machine executor with Docker.
