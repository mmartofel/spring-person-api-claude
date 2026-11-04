#!/bin/sh
# Skaffold before-deploy hook (skaffold.yaml): creates the stack's Secrets that do not exist yet in the deploy's
# namespace, so `skaffold run` needs no step before it (docs/DEPLOY-CONVENTION.md section 5). The database, Keycloak
# admin and client secret get random values; the test users get the same passwords as in Docker Compose (`viewer`,
# `editor`) unless DEMO_VIEWER_PASSWORD / DEMO_EDITOR_PASSWORD are set, which a publicly reachable cluster should do.
# An existing Secret is never changed: PostgreSQL keeps the password of its first start.
set -eu

context=${SKAFFOLD_KUBE_CONTEXT:-$(kubectl config current-context)}
namespace=${SKAFFOLD_NAMESPACES:-}
namespace=${namespace%%,*}
[ -n "$namespace" ] || namespace=$(kubectl config view --minify --context "$context" -o jsonpath='{..namespace}')
namespace=${namespace:-default}
k() { kubectl --context "$context" -n "$namespace" "$@"; }

random() { openssl rand -hex 16; }

# ensure <name> <key=value>...
ensure() {
  name=$1; shift
  if k get secret "$name" >/dev/null 2>&1; then
    echo "ensure-secrets: $namespace/$name exists, kept"
    return
  fi
  count=$#
  for pair in "$@"; do set -- "$@" "--from-literal=$pair"; done
  shift "$count"
  k create secret generic "$name" "$@" >/dev/null
  k label secret "$name" app.kubernetes.io/part-of=person-app >/dev/null
  echo "ensure-secrets: $namespace/$name created"
}

ensure postgres database=persons username=persons "password=$(random)"
ensure keycloak-admin username=admin "password=$(random)"
ensure spring-person-api-oidc-client "client-secret=$(random)"
ensure spring-person-api-demo-users \
  "viewer-password=${DEMO_VIEWER_PASSWORD:-viewer}" "editor-password=${DEMO_EDITOR_PASSWORD:-editor}"

echo "ensure-secrets: test user passwords: kubectl -n $namespace get secret spring-person-api-demo-users -o jsonpath='{.data.editor-password}' | base64 -d (or viewer-password)"
