# Screenshots

The images in this folder are referenced by [DEPLOYMENT.md](../DEPLOYMENT.md),
[TROUBLESHOOTING.md](../TROUBLESHOOTING.md) and the [README](../../README.md). This page lists how
each one was produced, so they can be retaken after changes.

## How the current images were produced

The `db-*.png` images are browser screenshots of phpMyAdmin taken by hand. All other images were
taken on 2026-10-07/08 against the minikube deployment described in the guides.
Each command was **run for real** and its actual output (including the ANSI colors) was rendered
into a terminal-style image with headless Chrome; the Swagger UI image is a headless Chrome
screenshot of the live page. A few details differ from the guide text:

- The port-forward used local port **18090** (8080 was busy on the capture machine), so the URLs
  in the images read `localhost:18090`.
- Lines longer than the window are clipped with `…`, like a terminal without line wrapping.
- `local-colored-logs.png` shows the jar run from a terminal with `-Dspring.output.ansi.enabled=always`
  (the same setting IntelliJ uses), piped through `cut` to keep the lines readable.

To retake them by hand instead, follow the steps below.

## How to capture

Use two terminal windows, both in the project root (`cd ~/Desktop/NCBA/country-info-service`):

- **Window A**: run the commands for the shot. Make it wide (about 160 columns) and run `clear`
  before each shot so only the relevant output is visible.
- **Window B**: take the screenshot. `screencapture -iW <file>` waits for you to click a window and
  saves it straight to `<file>` (the first time, macOS asks to allow Screen Recording for your
  terminal app):
  ```bash
  screencapture -iW docs/screenshots/<name>.png
  ```

Before starting, make sure the deployment is up and healthy:

```bash
DB_USERNAME=app DB_PASSWORD=app scripts/deploy.sh
```

## Deployment guide

| File | In window A, run | Capture when you see |
|---|---|---|
| `deploy-01-deploy-script.png` | `scripts/deploy.sh` | The final `==> Deployed` block with the pods, Service and HPA |
| `deploy-02-resources.png` | `kubectl -n country-info get deploy,pods,svc,hpa,pdb` | All resources listed, pods `1/1 Running` |
| `deploy-03-smoke-test.png` | `scripts/smoke-test.sh` | All 11 `PASS` lines and `Passed: 11  Failed: 0` |
| `deploy-04-api-call.png` | Terminal 1: `kubectl -n country-info port-forward svc/country-info-service 8080:80`. Window A: `curl -s -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name": "kenya"}' \| python3 -m json.tool` | The formatted JSON response (`201` or `200` with Kenya's data) |
| `deploy-05-swagger-ui.png` | With the port-forward running, open http://localhost:8080/swagger-ui.html and expand `POST /api/v1/countries` | The browser window with the endpoints listed |
| `deploy-06-rolling-update.png` | `kubectl -n country-info rollout restart deployment/country-info-service && kubectl -n country-info rollout status deployment/country-info-service` | `deployment "country-info-service" successfully rolled out` |
| `deploy-07-rollback.png` | `kubectl -n country-info rollout history deployment/country-info-service` then `kubectl -n country-info rollout undo deployment/country-info-service` | The revision list and `rolled back` |
| `deploy-08-hpa.png` | `kubectl -n country-info get hpa` then `kubectl -n country-info top pods` | The HPA with a CPU percentage (not `<unknown>`) and pod CPU/memory |
| `lb-round-robin.png` | With the ingress addon enabled (`minikube addons enable ingress`, then `scripts/deploy.sh`): `kubectl -n country-info get ingress,endpointslices`, then the two commands in DEPLOYMENT.md → "Verified locally (minikube)" | `100 200` and the per-pod counts (an even split) |

After `deploy-07`, run `scripts/deploy.sh` to put the cluster back in line with the manifests.

## Troubleshooting guide

Each scenario breaks the deployment on purpose and ends with a restore step. Run the restore before
moving to the next one.

| File | Section | Break it (window A) | Capture when you see | Restore |
|---|---|---|---|---|
| `ts-01-triage.png` | 1 | `kubectl -n country-info get pods -o wide` then `kubectl -n country-info get events --sort-by=.lastTimestamp \| tail -10` | Pods and the recent events | (nothing to restore) |
| `ts-02-image-pull-backoff.png` | 4.1 | `kubectl -n country-info set image deployment/country-info-service app=country-info-service:9.9.9`, wait ~25s, then `kubectl -n country-info get pods` and `kubectl -n country-info describe pod <new-pod> \| tail -8` | New pod `ImagePullBackOff`, old pods `Running`, `Back-off pulling image` | `kubectl -n country-info rollout undo deployment/country-info-service` |
| `ts-03-crashloop-access-denied.png` | 4.2 | `kubectl -n country-info set env deployment/country-info-service DB_PASSWORD=wrong-password`, wait ~20s, then `kubectl -n country-info get pods` and `kubectl -n country-info logs <new-pod> --previous \| grep -E "Access denied\|APPLICATION FAILED"` | `CrashLoopBackOff` and `Access denied for user 'app'` | `kubectl -n country-info rollout undo deployment/country-info-service` |
| `ts-04-create-container-config-error.png` | 4.3 | `kubectl -n country-info delete secret country-info-db`, then `kubectl -n country-info rollout restart deployment/country-info-service`, wait ~15s, then `kubectl -n country-info get pods` and `kubectl -n country-info describe pod <new-pod> \| grep -E "Warning +Failed"` | `CreateContainerConfigError` and `secret "country-info-db" not found` | `kubectl -n country-info create secret generic country-info-db --from-literal=username=app --from-literal=password=app` |
| `ts-05-no-traffic-endpoints.png` | 4.4 | The five commands in section 4.4 (patch the selector, get pods, get endpoints, in-cluster curl, selector vs labels) | Pods `Running`, endpoints `<none>`, `HTTP 000`, and the mismatching selector/label | The `patch` command at the end of section 4.4 |
| `ts-06-probe-events.png` | 4.5 | `kubectl -n country-info rollout restart deployment/country-info-service`, wait ~30s, then `kubectl -n country-info get events --field-selector reason=Unhealthy --sort-by=.lastTimestamp \| tail -5` | `Startup probe failed: ... connection refused` events | (nothing to restore) |
| `ts-07-request-trace.png` | 6 | With the port-forward running: `curl -s -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name": "rwanda"}'`, copy the `requestId`, then `kubectl -n country-info logs -l app.kubernetes.io/name=country-info-service --tail=2000 --prefix \| grep <requestId> \| cut -d'\|' -f1,2,5,6` | All log lines of that one request, from `Request received` to `Request completed` | (nothing to restore) |
| `ts-08-soap-outage-circuit-breaker.png` | 7 | The "Reproduce it" commands in section 7 (set the dead SOAP URL, restart the port-forward, submit six countries, read the metrics) | Five `not responding` 503s, one fast `temporarily unavailable` 503, and `state="open"` | `kubectl -n country-info rollout undo deployment/country-info-service` |
| `ts-09-diagnostics-bundle.png` | 11 | The diagnostics bundle command, then `ls diag-*/` | `wrote diag-....tgz` and the files in the bundle. Delete it afterwards: `rm -rf diag-*` | (nothing to restore) |

When all troubleshooting shots are done, re-sync the cluster:

```bash
scripts/deploy.sh
```

## README

| File | How | Capture when you see |
|---|---|---|
| `local-colored-logs.png` | Run the app from IntelliJ (`CountryInfoServiceApplication`), then `curl -s -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name": "kenya"}'` and the same with `"narnia"` | The IntelliJ console showing cyan (request received), green (success) and red (404) lines. Take it with `screencapture -iW docs/screenshots/local-colored-logs.png` and click the IntelliJ window |
| `db-01-tables.png` | Start phpMyAdmin (README → "Browse the stored data"), log in as `app`/`app`, open `countrydb` | The three tables with row counts |
| `db-02-country-info.png` | Click **Browse** on `country_info` | The stored countries |
| `db-03-language.png` | Click **Browse** on `language` | The languages with their `country_id` |
