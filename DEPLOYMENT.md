# Deployment

PHASE 3A provides Docker Compose support for the User Service and its dedicated
PostgreSQL database. It is not a production
deployment manifest.

Production deployment must provide TLS, trusted hostnames, managed databases,
durable Keycloak storage, secret injection, backups, network policies, resource
limits, autoscaling, health probes and centralized telemetry. The Keycloak
`start-dev` command in Compose must never be used in production.

The deployment secret store must inject Keycloak admin/database credentials and
the User Service database password. Never bake these values into an image.

Each backend service has a multi-stage Dockerfile and runs as a non-root numeric
user. PostgreSQL ports should remain private ClusterIP services;
only the ingress/gateway is externally reachable. Kubernetes resources,
immutable image digests and environment promotion are deferred to the dedicated
deployment phase.
