# Deploying to Kubernetes

**Production, GitOps** (GitHub Actions → registry → gitops repo → Argo CD → OpenShift): every
change reaches the cluster through Git. See [CI/CD to OpenShift (GitOps)](#cicd-to-openshift-gitops).

This guide also shows how we deployed and verified it **locally** on minikube (Docker driver,
running on Colima on macOS), using the same Dockerfile and manifests the pipeline deploys. See
[Local deployment (minikube)](#local-deployment-minikube).

The deployment contains only the **country-info-service** microservice. The MySQL database is
external: a managed database (RDS, Cloud SQL, Azure Database) in production, and the Docker MySQL
from the [README](../README.md#1-start-mysql) locally. The manifests are plain Kubernetes and also
work on other clusters (see [Other clusters](#other-clusters)).

## What gets deployed

```
                         namespace: country-info
 ┌──────────────────────────────────────────────────────────────────────┐
 │                                                                      │
 │   Service country-info-service (ClusterIP :80)  ◄── load-balances    │
 │          │                                                           │
 │          ▼                                                           │
 │   Deployment country-info-service     HPA: 2-5 pods @ 70% CPU        │
 │   ├── pod (Spring Boot, :8080)         PDB: min 1 available          │
 │   └── pod (Spring Boot, :8080)                                       │
 │          env: ConfigMap country-info-config + Secret country-info-db │
 │                                                                      │
 └──────────┬───────────────────────────────────────┬───────────────────┘
            │ JDBC (DB_URL)                          │ HTTP (SOAP)
            ▼                                        ▼
   External MySQL                           webservices.oorsprong.org
   (managed DB / local Docker MySQL)        CountryInfoService.wso
```

| File | Resource | Purpose |
|---|---|---|
| `Dockerfile` | image | Multi-stage build (JDK → JRE 21), layered jar, non-root user (uid 1001), heap sized from the container limit |
| `k8s/namespace.yaml` | Namespace | Isolates everything in `country-info` |
| `k8s/app/config.env` | ConfigMap (generated) | Non-secret config: database URL, SOAP URL and timeouts, log level |
| `k8s/app/deployment.yaml` | Deployment | 2 replicas, zero-downtime rolling updates, startup/readiness/liveness probes, resource limits, read-only root filesystem |
| `k8s/app/service.yaml` | Service (ClusterIP) | Load-balances requests across the ready pods |
| `k8s/app/hpa.yaml` | HorizontalPodAutoscaler | Scales 2 → 5 pods when average CPU > 70% |
| `k8s/app/pdb.yaml` | PodDisruptionBudget | Keeps at least 1 pod up during node drains and upgrades |
| `k8s/kustomization.yaml` | Kustomize | Ties it together; sets the namespace, labels and image tag |
| *(created by `deploy.sh`)* | Secret `country-info-db` | Database username and password, from your environment. **Never committed** |

## CI/CD to OpenShift (GitOps)

In production the service is not deployed with `deploy.sh`. A commit flows to the cluster through
CI (GitHub Actions) and GitOps CD (Argo CD), so the cluster always matches what is in Git:

![ms-country-info GitOps CI/CD flow](images/gitops-cicd-flow.webp)

| Step | What happens | Tooling and notes |
|---|---|---|
| 1 | Developer pushes to the app repo `ms-country-info` | Pull request + review on `main` |
| 2 | The push triggers the CI workflow | GitHub Actions |
| CI | Build and run the tests (`./mvnw verify`), build the image tagged with the **commit SHA**, scan it (e.g. Trivy, fail on HIGH/CRITICAL), push it | The same `Dockerfile` as locally |
| 3 | The image is pushed to the registry as `ms-country-info:<commit-sha>` | Docker Hub, ECR, Harbor or the OpenShift internal registry |
| 4 | CI commits the new tag to the **gitops repo** (`kustomize edit set image ...`) | The gitops repo holds the manifests from `k8s/` with one overlay per environment |
| 5 | Argo CD notices the commit (webhook, or polling every 3 minutes as a fallback) | Compares desired state (Git) with live state (cluster) |
| 6 | Argo CD syncs: applies the manifests and reports **Synced / Healthy** | Health uses the Deployment rollout, i.e. the readiness probes |
| 7 | The kubelet pulls the image with an `imagePullSecret` | Private registry credentials stored as a Secret in the namespace |

Why it is built this way:

- **Tag images with the commit SHA, never `:latest`.** Every build gets a unique, traceable tag. With `:latest` the gitops repo would not change, Argo CD would see no diff and nothing would be deployed.
- **Git is the single source of truth.** Every deployment is a commit, so it is reviewed, audited and reverted with `git revert` (Argo CD then rolls back). No one needs `kubectl apply` access to production.
- **Alternative to step 4:** Argo CD Image Updater can watch the registry and commit the new tag to the gitops repo itself, so CI never needs write access to it.
- **Secrets stay out of Git:** the `country-info-db` Secret and the registry `imagePullSecret` come from a secrets manager (External Secrets Operator, Sealed Secrets or Vault), not from the gitops repo.

What changes on **OpenShift**:

- `oc` works like `kubectl` (`oc get pods`, `oc logs`, `oc rollout status ...`), and a project is a namespace.
- OpenShift runs each pod with a random UID from the project's range, and its default `restricted-v2` SCC rejects the fixed `runAsUser: 1001`. Remove `runAsUser`/`runAsGroup`/`fsGroup` from `deployment.yaml` in the OpenShift overlay and let OpenShift assign them. The image still works with any UID: the application files in `/app` are world-readable, and the app only writes to `/tmp`, which is an `emptyDir`.
- Expose the API with a **Route** (TLS edge termination) instead of an Ingress:
  ```bash
  oc -n country-info create route edge country-info --service=country-info-service
  ```

An Argo CD `Application` for this service (in the gitops repo):

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ms-country-info
  namespace: openshift-gitops
spec:
  project: default
  source:
    repoURL: https://github.com/<org>/gitops-repo.git
    targetRevision: main
    path: apps/ms-country-info/overlays/prod      # kustomize overlay based on k8s/
  destination:
    server: https://kubernetes.default.svc
    namespace: country-info
  syncPolicy:
    automated:
      prune: true        # delete resources removed from Git
      selfHeal: true     # revert manual changes made in the cluster
    syncOptions:
      - CreateNamespace=true
```

## Local deployment (minikube)

This is how we deployed and tested the service for this exercise: one script on a laptop, using the
same Dockerfile and manifests that the GitOps pipeline deploys to OpenShift.

### Prerequisites

| Tool | Check | Install (macOS) |
|---|---|---|
| Docker engine | `docker info` | `brew install colima docker docker-buildx` then `colima start --cpu 4 --memory 8` |
| kubectl | `kubectl version --client` | `brew install kubectl` |
| minikube | `minikube version` | `brew install minikube` |

Start the cluster (once). `metrics-server` is needed for the autoscaler:

```bash
minikube start --driver=docker --cpus=2 --memory=4096
minikube addons enable metrics-server
```

**A MySQL database the cluster can reach**, with a `countrydb` schema and a user that can create
tables (Flyway creates and migrates them on startup). For local testing, start the Docker MySQL
from the README; pods reach it at `host.minikube.internal:3306`, which is already the `DB_URL` in
`k8s/app/config.env`. To use another database, change `DB_URL` there.

### Deploy (one command)

From the project root, passing the database credentials the first time:

```bash
DB_USERNAME=app DB_PASSWORD=app scripts/deploy.sh
```

The script:

1. Builds the image `country-info-service:1.0.0` (the first build takes ~6 minutes while Maven downloads dependencies; later builds reuse the cache).
2. Loads it into minikube with `minikube image load` (no registry needed).
3. Creates the namespace and the Secret `country-info-db` from `DB_USERNAME`/`DB_PASSWORD`. Later deploys reuse the Secret, so the credentials are only needed again to change them.
4. Applies the manifests (`kubectl kustomize k8s/ | kubectl apply -f -`).
5. Waits for the rollout and prints the pods, Service and HPA. If the rollout fails, it shows the pods and how to read their logs (usually the database at `DB_URL` is not reachable).

Expected end of the output:

```
pod/country-info-service-67d846ff7f-ccgxg   1/1   Running   0   10s
pod/country-info-service-67d846ff7f-kl9cl   1/1   Running   0   10s
service/country-info-service   ClusterIP   10.100.84.69   <none>   80/TCP   10s
horizontalpodautoscaler.autoscaling/country-info-service   Deployment/country-info-service   cpu: <unknown>/70%   2   5   2
```

The HPA shows `cpu: <unknown>/70%` for about a minute after pods start; that is normal (metrics-server needs a first sample).

![deploy.sh output](screenshots/deploy-01-deploy-script.png)

All resources in the namespace:

```bash
kubectl -n country-info get deploy,pods,svc,hpa,pdb
```

![Deployed resources](screenshots/deploy-02-resources.png)

The script is **idempotent**: running it again with no changes does not restart anything. Pods
roll only when the image content, the config or the database credentials changed.

### Verify

Automated end-to-end check (health, create via SOAP, idempotent re-create, 404, 400, list, get,
update, stale-version 409, delete):

```bash
scripts/smoke-test.sh
```

```
  PASS GET /actuator/health                          200
  PASS POST ghana (create via SOAP)                  201
  ...
  PASS GET /30 after delete                          404
Passed: 11  Failed: 0
```

![Smoke test](screenshots/deploy-03-smoke-test.png)

Manual access through a port-forward:

```bash
kubectl -n country-info port-forward svc/country-info-service 8080:80
```

Then, in another terminal:

```bash
curl -s http://localhost:8080/actuator/health
```

```bash
curl -s -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name": "kenya"}'
```

```bash
curl -s "http://localhost:8080/api/v1/countries?page=0&size=10&sort=name,asc"
```

Swagger UI: http://localhost:8080/swagger-ui.html · Metrics: http://localhost:8080/actuator/prometheus

![API call through the port-forward](screenshots/deploy-04-api-call.png)

![Swagger UI](screenshots/deploy-05-swagger-ui.png)

## Day-2 operations

The commands below operate the **local** deployment directly. In **production** the same operations
go through Git, and Argo CD applies them:

| Operation | Production (GitOps) | Local (minikube) |
|---|---|---|
| Deploy a new version | Merge to `main`: CI builds `ms-country-info:<commit-sha>` and bumps the tag in the gitops repo | `scripts/deploy.sh <tag>` |
| Roll back | `git revert` the tag bump in the gitops repo (or roll back to a previous sync in Argo CD) | `kubectl rollout undo` |
| Change configuration | Commit the change to the environment's `config.env` in the gitops repo | Edit `k8s/app/config.env`, run `scripts/deploy.sh` |
| Change DB credentials | Update the value in the secrets manager; External Secrets syncs the Secret | `DB_PASSWORD=... scripts/deploy.sh` |
| Scale | Commit new `minReplicas`/`maxReplicas` to the HPA in the gitops repo | Edit `k8s/app/hpa.yaml`, run `scripts/deploy.sh` |

### Deploy a new version

Change the code, then build and roll out under a new tag:

```bash
scripts/deploy.sh 1.0.1
```

The Deployment uses `maxSurge: 1, maxUnavailable: 0`: a new pod must pass its readiness probe
before an old one is removed, and old pods drain gracefully (`preStop` 10s, then Spring's graceful
shutdown for up to 30s). Tested: **540/540 requests succeeded** from inside the cluster during a
full rolling restart.

Watch it:

```bash
kubectl -n country-info rollout status deployment/country-info-service
```

![Rolling update](screenshots/deploy-06-rolling-update.png)

### Roll back

```bash
kubectl -n country-info rollout undo deployment/country-info-service
```

```bash
kubectl -n country-info rollout history deployment/country-info-service
```

![Rollout history and rollback](screenshots/deploy-07-rollback.png)

### Change configuration

Edit `k8s/app/config.env` (e.g. `APP_LOG_LEVEL=DEBUG`, `SOAP_READ_TIMEOUT=10s` or a different
`DB_URL`) and run `scripts/deploy.sh` again. Kustomize gives the ConfigMap a new hashed name, so
the pods roll onto the new config automatically. The database timeouts listed in the README
(`DB_QUERY_TIMEOUT_MS`, `DB_LOCK_WAIT_TIMEOUT_S`, ...) can be added there the same way.

### Change the database credentials

```bash
DB_USERNAME=app DB_PASSWORD='<new password>' scripts/deploy.sh
```

This updates the Secret and rolls the pods onto it.

### Scale

Automatic: the HPA keeps 2-5 replicas based on CPU. Check it with:

```bash
kubectl -n country-info get hpa
```

```bash
kubectl -n country-info top pods
```

![HPA and pod resource usage](screenshots/deploy-08-hpa.png)

To change the range, edit `minReplicas`/`maxReplicas` in `k8s/app/hpa.yaml` and redeploy. The
service is stateless (all state is in the database, caches are per pod and only hold reference
data), so any number of replicas can serve any request. Keep `replicas × DB_POOL_SIZE` (10 per
pod) below the database's connection limit.

## Tear down

Remove the microservice but keep the namespace and the database Secret (a later `deploy.sh` needs
no credentials):

```bash
scripts/teardown.sh
```

Remove everything, including the Secret:

```bash
scripts/teardown.sh --all
```

Neither touches the database or its data.

## Other clusters

The manifests are cluster-agnostic. On a managed cluster (EKS, GKE, AKS, OpenShift):

1. **Push the image to a registry** instead of `minikube image load`, and point `k8s/kustomization.yaml` at it:
   ```bash
   docker build -t <registry>/country-info-service:1.0.0 .
   ```
   ```bash
   docker push <registry>/country-info-service:1.0.0
   ```
   ```yaml
   images:
     - name: country-info-service
       newName: <registry>/country-info-service
       newTag: 1.0.0
   ```
2. **Point `DB_URL`** in `k8s/app/config.env` at the managed database, and allow the cluster's nodes or pods to reach it (security group, firewall rule, private endpoint).
3. **Create the Secret** with `kubectl create secret generic country-info-db --from-literal=username=... --from-literal=password=...` (as `deploy.sh` does) or, better, from a secrets manager (External Secrets Operator, Sealed Secrets, Vault).
4. **Expose the service** with an Ingress (TLS) or a `LoadBalancer` Service instead of port-forwarding.
5. **Scrape metrics**: the pods carry `prometheus.io/*` annotations for Prometheus; logs go to stdout for the cluster's log collector (Fluent Bit, Loki, CloudWatch, etc.).

## Production hardening checklist

Already in place: non-root user, read-only root filesystem, all capabilities dropped, seccomp
`RuntimeDefault`, resource requests/limits, three kinds of probes, graceful shutdown, PDB, HPA,
secrets out of git, schema migrations via Flyway, database and SOAP timeouts, structured logs to
stdout, Prometheus metrics.

Still recommended for a real environment:

- NetworkPolicy restricting egress to the database endpoint and the SOAP host.
- Ingress with TLS and authentication (OAuth2/JWT) in front of the API.
- Image scanning (Trivy/Grype) and signing (cosign) in CI; pin base images by digest.
- A distributed cache (Redis) if the per-pod Caffeine cache hit rate becomes too low at high replica counts.
- Alerts on `resilience4j_circuitbreaker_state{state="open"}`, 5xx rate, p95 latency and pod restarts.
