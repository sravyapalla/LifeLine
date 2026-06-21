# LifeLine Kubernetes Deployment

These manifests provide a production-shaped baseline for deploying LifeLine behind the gateway.

The default manifests assume PostgreSQL/PostGIS, Redis, and Kafka are reachable as managed services or separately provisioned platform dependencies. Use Docker Compose for the fastest local full-stack run.

## Apply Order

```powershell
kubectl apply -f deploy/k8s/base/namespace.yaml
kubectl apply -f deploy/k8s/base/configmap.yaml
kubectl apply -f deploy/k8s/base/secrets.example.yaml
kubectl apply -f deploy/k8s/base/operations-service.yaml
kubectl apply -f deploy/k8s/base/boundary-services.yaml
kubectl apply -f deploy/k8s/base/gateway-service.yaml
kubectl apply -f deploy/k8s/base/frontend.yaml
```

Before applying `secrets.example.yaml`, replace every `REPLACE_ME_*` value or create an equivalent `lifeline-secrets` secret with your platform secret manager.

## Image Tags

The manifests use placeholder image tags:

- `lifeline/operations-service:latest`
- `lifeline/gateway-service:latest`
- `lifeline/incident-service:latest`
- `lifeline/resource-service:latest`
- `lifeline/dispatch-service:latest`
- `lifeline/notification-service:latest`
- `lifeline/audit-service:latest`
- `lifeline/simulation-service:latest`
- `lifeline/frontend:latest`

In a real deployment, pin immutable tags such as commit SHAs.
