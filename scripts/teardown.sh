#!/usr/bin/env bash
# Removes the country-info-service microservice from the cluster. The external database is never touched.
# Usage: scripts/teardown.sh            removes the app's resources, keeps the namespace and DB Secret
#        scripts/teardown.sh --all      deletes the whole namespace, including the DB Secret
set -euo pipefail

NAMESPACE="country-info"

if [[ "${1:-}" == "--all" ]]; then
  echo "Deleting namespace ${NAMESPACE} (all resources and the DB secret)..."
  kubectl delete namespace "${NAMESPACE}" --ignore-not-found --wait=true
else
  echo "Deleting the app's resources (keeping the namespace and the DB secret)..."
  # Only resources from k8s/ carry this label; the secret created by deploy.sh does not, so it survives.
  kubectl -n "${NAMESPACE}" delete deployment,service,configmap,hpa,pdb,ingress \
    -l app.kubernetes.io/part-of=country-info --ignore-not-found --wait=true
fi
kubectl get all -n "${NAMESPACE}" 2>/dev/null || true
