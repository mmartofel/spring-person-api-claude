# Deployment convention (Kubernetes and OpenShift)

One method for every repo of an application: the API (with its PostgreSQL and Keycloak) and the UI made from
crud-ui-blueprint. It is written in the UI repo and copied **unchanged** into the API repo, like
`docs/API-CONTRACT.md`. A change goes into both copies in the same session.

The rule behind it: **nothing in git names a cluster, host, registry, namespace or real secret.** The same files
deploy to any Kubernetes or OpenShift cluster, in any namespace. What differs per environment comes only from an
environment overlay or a Skaffold flag.

## 1. Layout
```
deploy/
  base/                   plain Kubernetes, valid in any cluster: Deployments, Services, ConfigMaps, PVCs,
                          probes, resources, securityContext, app.kubernetes.io/* labels. No Route, Ingress, host,
                          registry or secret values.
  overlays/
    local/                the default: a local cluster (kind, minikube, Docker Desktop), with port forwarding.
    openshift/            + a Route per public entry point (no spec.host: OpenShift generates one) + Topology labels.
    kubernetes/           + an Ingress per public entry point (no host: an environment overlay sets it).
  environments/
    example/              committed: how an environment overlay looks.
    <env>/                git-ignored: one environment's host, TLS, image pins, settings.
skaffold.yaml
```
- An overlay starts from `../../base` and adds only what its target needs. An environment overlay starts from one
  overlay (`../../overlays/openshift`) and patches only what that environment needs.
- Use only kustomize features that kustomize 3.8 (Skaffold's external `kustomize`), `kubectl -k` and `oc -k` all
  know: `resources`, `patches` (file or inline JSON 6902 with `target`), `configMapGenerator`, `secretGenerator`.
  No `labels`, `replacements` or `components`. Labels are written on the objects.

## 2. Skaffold
The same shape in every repo:

| Item | Value |
|---|---|
| Profiles | none (default: `deploy/overlays/local`), `openshift`, `kubernetes` |
| Kustomize path | `'{{ default "deploy/overlays/<profile>" .DEPLOY_OVERLAY }}'`: `DEPLOY_OVERLAY` points at an environment overlay |
| Image | the bare artifact name in the manifests (`image: <app>`); the registry comes only from `--default-repo` |
| Build | needs no daemon setup: Jib (API), or `deploy/build-image.sh` (UI, a custom builder using docker or podman, whichever is installed); built for the platform Skaffold detects on the cluster nodes |
| Tag | `tagPolicy: gitCommit` |
| Namespace | only from `-n` or the current context; never `namespace:` in a manifest or kustomization |
| Port forwarding | only in the default profile (`local`); `openshift` and `kubernetes` remove it |
| Hooks | `deploy.kubectl.hooks.before`: the API repo's `deploy/ensure-secrets.sh` (section 5) |

Run from the repo root. Commands, identical in every repo; installing the application is one line per repo, API first:
```bash
skaffold dev                                                              # local cluster
skaffold run -p openshift --default-repo=quay.io/<org> -n <namespace>     # or -p kubernetes
skaffold run -p openshift --default-repo=quay.io/<org> -n <namespace> \
  --label app.kubernetes.io/version=$(git rev-parse --short HEAD)         # with the Topology version label
DEPLOY_OVERLAY=deploy/environments/<env> skaffold run -p openshift ...    # an environment overlay
skaffold delete -p openshift -n <namespace>
```
The application is installed repo by repo, into **one namespace** (any; nothing assumes a name): first the API repo
(PostgreSQL, Keycloak, API), then the UI repo. Each `skaffold run` is complete on its own, with no step before it, and
each `skaffold delete` removes only what its repo installed. The repos find each other only through the fixed Service
names in section 3.

## 3. Names and labels
- Object names: the app's own name (`<api>`, `<ui>`); the stack's dependencies use fixed Service names: `postgres`,
  `keycloak`. Other repos rely on them (the UI's defaults are `http://<api>:8080` and `http://keycloak:8080`).
- Every Deployment, Service, Route and Ingress carries, in `metadata.labels`:
  - `app.kubernetes.io/name`: the object's app (`<api>`, `postgres`, `keycloak`, `<ui>`)
  - `app.kubernetes.io/component`: `backend`, `database`, `auth` or `frontend`
  - `app.kubernetes.io/part-of`: the application, the same in every repo (`<app>`)
- Selectors stay `app: <name>`. Selectors can't change, so labels are never added to them.
- `app.kubernetes.io/version` is set at deploy time (`--label`), never in a file.

## 4. OpenShift Topology (only in `overlays/openshift`)
- One strategic merge patch file per Deployment, on its metadata only (`topology-<app>.yaml`: `topology-api.yaml`,
  `topology-postgres.yaml`, `topology-keycloak.yaml`, `topology-ui.yaml`):
  - `app.openshift.io/runtime`: `spring`, `postgresql`, `keycloak`, `nginx`
  - `app.openshift.io/vcs-uri`: the repo URL (on the app's own Deployment)
- `app.openshift.io/connects-to` is an arrow **from the caller to what it calls**. Each repo declares only the arrows
  of its own Deployments: the UI → `<api>` and `keycloak`; the API → `postgres` and `keycloak`.
- Keycloak has no console icon: it carries the logo in the `app.openshift.io/custom-icon` annotation.

## 5. Configuration and secrets
- Runtime settings live in a ConfigMap in `base`, with defaults that hold inside any cluster (Service names, not
  hosts). An environment overlay patches a value only when its stack differs.
- `base` references Secrets by name only. Fixed names: `postgres` (`database`, `username`, `password`),
  `keycloak-admin` (`username`, `password`), `<api>-oidc-client` (`client-secret`), and for test users
  `<api>-demo-users`.
- The repo that needs a Secret creates it. A Skaffold `before` deploy hook (`deploy/ensure-secrets.sh`, in every
  profile) creates each missing Secret with random values in the deploy's namespace and context
  (`$SKAFFOLD_NAMESPACES`, `$SKAFFOLD_KUBE_CONTEXT`, else the context's namespace). It never changes an existing one,
  since PostgreSQL keeps the password of its first start. So `skaffold run` needs no step before it, and no secret
  value is in git.
- An environment that manages its own Secrets (an operator, a vault) creates them before the first deploy; the hook
  then leaves them alone.
- Values the realm needs (the client secret, test user passwords) reach Keycloak's import as environment variables
  from those Secrets (`${ENV}` placeholders in the realm file). Read them with `kubectl get secret ... -o jsonpath`.
- The realm file (`deploy/base/realm.json`) holds no host and no secret value. Only Docker Compose (local
  development) keeps fixed demo values.

## 6. One public host, no host in any file
The browser reaches the whole application on the UI's host:
- `/`: the UI
- `/api/` and `/actuator/{health,info}`: proxied by the UI's nginx to `API_UPSTREAM`
- `/realms/<realm>/` and `/resources/`: proxied to `OIDC_UPSTREAM` (Keycloak). Only that realm: the master realm and
  `/admin` are never public.

So:
- The UI's `OIDC_AUTHORITY` is a path (`/realms/<realm>`), resolved against the page's origin.
- Keycloak runs with `KC_PROXY_HEADERS=xforwarded` and no `KC_HOSTNAME`. It builds every URL, including the token
  issuer, from the host the browser used.
- The UI client in the realm has `rootUrl: ${authBaseUrl}`, relative redirect URIs (`/*`), post-logout `/*` and
  `webOrigins: ["+"]`. They resolve to the host the request came through, and a foreign host is rejected. Local
  development keeps `http://localhost:5173/*` as well.
- One Route (OpenShift, generated host) or Ingress (Kubernetes, host from the environment overlay) points at the UI.
  The API may have its own Route (Swagger UI, machine clients). Keycloak has none.
- Token validation in the API uses the JWK set inside the cluster (`http://keycloak:8080/realms/<realm>/...`). When it
  also checks `iss`, the public issuer is one value in the environment overlay (`OIDC_ISSUER`).

## 7. Checks
- CI renders every overlay (`kubectl kustomize deploy/overlays/<x>`) and the example environment.
- `grep` of the rendered overlays finds no cluster domain, registry or namespace. `localhost` may appear only in the
  realm's development redirect.
- A change is verified in two different namespaces with no file changed between them.
