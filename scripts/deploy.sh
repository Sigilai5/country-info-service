#!/usr/bin/env bash
# Builds the image, loads it into minikube and deploys the country-info-service microservice to the
# "country-info" namespace. The MySQL database is external (see k8s/app/config.env for DB_URL).
# Safe to re-run: it updates the existing deployment (rolling update) only when something changed.
#
# Usage:
#   DB_USERNAME=app DB_PASSWORD=app scripts/deploy.sh [image-tag]    first deploy (creates the DB Secret)
#   scripts/deploy.sh [image-tag]                                    later deploys (reuses the Secret)
# Default tag: 1.0.0. Passing DB_PASSWORD again updates the Secret and rolls the pods.
set -euo pipefail

TAG="${1:-1.0.0}"
IMAGE="country-info-service:${TAG}"
NAMESPACE="country-info"
SECRET="country-info-db"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

for cmd in docker kubectl minikube; do
  command -v "$cmd" >/dev/null || fail "'$cmd' is not installed or not on PATH"
done
minikube status >/dev/null 2>&1 || fail "minikube is not running. Start it with: minikube start"

log "Building image ${IMAGE}"
docker build -t "${IMAGE}" "${ROOT}"

log "Loading image into minikube (no registry needed)"
minikube image load "${IMAGE}" --overwrite=true

log "Creating namespace ${NAMESPACE}"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Database credentials live in a Secret, never in git. They must match the external database.
if [[ -n "${DB_PASSWORD:-}" ]]; then
  log "Creating/updating database secret ${SECRET} (user ${DB_USERNAME:-app})"
  kubectl -n "${NAMESPACE}" create secret generic "${SECRET}" \
    --from-literal=username="${DB_USERNAME:-app}" \
    --from-literal=password="${DB_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -
elif kubectl -n "${NAMESPACE}" get secret "${SECRET}" >/dev/null 2>&1; then
  log "Database secret ${SECRET} already exists, reusing it"
else
  fail "Secret ${SECRET} does not exist yet. Provide the database credentials, e.g.:
       DB_USERNAME=app DB_PASSWORD=app scripts/deploy.sh"
fi

# Stamp hashes into the pod template so pods roll exactly when the image content or the DB
# credentials changed (even when the tag is reused), and are left alone otherwise. The image layer
# list is used rather than the image ID, which changes on every build (BuildKit adds a timestamped
# provenance attestation); the Secret's resourceVersion only changes when its content changes.
IMAGE_ID="$(docker image inspect -f '{{json .RootFS.Layers}}' "${IMAGE}" | shasum -a 256 | cut -c1-12)"
SECRET_VERSION="$(kubectl -n "${NAMESPACE}" get secret "${SECRET}" -o jsonpath='{.metadata.resourceVersion}')"

log "Applying manifests (kubectl kustomize k8s/ | kubectl apply)"
( cd "${ROOT}/k8s" && kubectl kustomize . \
    | sed -e "s|image: country-info-service:.*|image: ${IMAGE}|" \
          -e "s|prometheus.io/port: \"8080\"|prometheus.io/port: \"8080\"\n        country-info/image-id: \"${IMAGE_ID}\"\n        country-info/db-secret-version: \"${SECRET_VERSION}\"|" \
    | kubectl apply -f - )

# Local entry point with round-robin load balancing (the equivalent of the OpenShift Route), only when
# the minikube ingress addon is enabled: minikube addons enable ingress
if kubectl get ingressclass nginx >/dev/null 2>&1; then
  log "Exposing the service through ingress-nginx (round robin)"
  kubectl -n ingress-nginx patch configmap ingress-nginx-controller --type merge \
    -p '{"data":{"load-balance":"round_robin"}}' >/dev/null
  kubectl apply -f "${ROOT}/k8s/local/ingress.yaml"
fi

log "Waiting for the rollout to finish"
if ! kubectl -n "${NAMESPACE}" rollout status deployment/country-info-service --timeout=300s; then
  echo
  kubectl -n "${NAMESPACE}" get pods
  fail "Rollout did not finish. Check the logs (is the database at DB_URL reachable?):
       kubectl -n ${NAMESPACE} logs deploy/country-info-service --tail=50"
fi

log "Deployed"
kubectl -n "${NAMESPACE}" get pods,svc,hpa -o wide
cat <<MSG

Next steps:
  kubectl -n ${NAMESPACE} port-forward svc/country-info-service 8080:80
  curl -s http://localhost:8080/actuator/health
  scripts/smoke-test.sh
MSG
