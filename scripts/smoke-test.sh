#!/usr/bin/env bash
# End-to-end check of a running deployment: health, then create -> list -> get -> update -> delete.
# Usage: scripts/smoke-test.sh [base-url]
#   With no URL it port-forwards the Kubernetes Service on a free local port for the duration of the test.
set -euo pipefail

NAMESPACE="country-info"
BASE_URL="${1:-}"
PF_PID=""
BODY="$(mktemp)"
cleanup() { if [[ -n "${PF_PID}" ]]; then kill "${PF_PID}" 2>/dev/null; wait "${PF_PID}" 2>/dev/null; fi; rm -f "${BODY}"; }
trap cleanup EXIT

if [[ -z "${BASE_URL}" ]]; then
  PORT=18080
  kubectl -n "${NAMESPACE}" port-forward svc/country-info-service "${PORT}:80" >/dev/null 2>&1 &
  PF_PID=$!
  BASE_URL="http://localhost:${PORT}"
  for _ in $(seq 1 20); do curl -sf -o /dev/null "${BASE_URL}/actuator/health" && break; sleep 0.5; done
fi

API="${BASE_URL}/api/v1/countries"
PASS=0; FAIL=0
check() { # check <description> <expected-status> <actual-status>
  if [[ "$3" == "$2" ]]; then PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %-45s %s\n' "$1" "$3"
  else FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %-45s expected %s, got %s\n' "$1" "$2" "$3"
       printf '       response: %s\n' "$(cat "${BODY}")"; fi
}
status() { curl -s -o "${BODY}" -w '%{http_code}' "$@"; }
json() { python3 -c "import json,sys; d=json.load(open(sys.argv[2])); print(eval(sys.argv[1]))" "$1" "${BODY}"; }

echo "Smoke test against ${BASE_URL}"
check "GET /actuator/health"                   200 "$(status "${BASE_URL}/actuator/health")"
check "POST ghana (create via SOAP)"            201 "$(status -X POST -H 'Content-Type: application/json' -d '{"name":"ghana"}' "${API}")"
ID="$(json "d['data']['id']")"
check "POST GHANA again (already stored)"       200 "$(status -X POST -H 'Content-Type: application/json' -d '{"name":"GHANA"}' "${API}")"
check "POST narnia (unknown country)"           404 "$(status -X POST -H 'Content-Type: application/json' -d '{"name":"narnia"}' "${API}")"
check "POST empty name (validation)"            400 "$(status -X POST -H 'Content-Type: application/json' -d '{"name":""}' "${API}")"
check "GET list"                                200 "$(status "${API}?page=0&size=5&sort=name,asc")"
check "GET /${ID}"                              200 "$(status "${API}/${ID}")"
VERSION="$(json "d['data']['version']")"
UPDATE_BODY="$(printf '{"isoCode":"GH","name":"Ghana","capitalCity":"Accra (updated)","languages":[{"isoCode":"eng","name":"English"}],"version":%s}' "${VERSION}")"
STALE_BODY="$(printf '{"isoCode":"GH","name":"Ghana","languages":[],"version":%s}' "${VERSION}")"
check "PUT /${ID} (update capital)"             200 "$(status -X PUT -H 'Content-Type: application/json' -d "${UPDATE_BODY}" "${API}/${ID}")"
check "PUT /${ID} with stale version"           409 "$(status -X PUT -H 'Content-Type: application/json' -d "${STALE_BODY}" "${API}/${ID}")"
check "DELETE /${ID}"                           200 "$(status -X DELETE "${API}/${ID}")"
check "GET /${ID} after delete"                 404 "$(status "${API}/${ID}")"

echo "Passed: ${PASS}  Failed: ${FAIL}"
[[ "${FAIL}" -eq 0 ]]
