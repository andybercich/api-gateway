# API Gateway

The `api-gateway` is the single public HTTP entry point of the microservices architecture.

It is responsible for routing external requests to internal microservices while providing centralized authentication, rate limiting, service discovery and request tracing.

The Gateway is the only application component whose HTTP port is exposed externally. The remaining microservices communicate through the internal network and are not directly accessible from outside the system.

## Responsibilities

The main responsibilities of the API Gateway are:

* Provide the single public HTTP entry point.
* Route requests to internal microservices.
* Discover services through Eureka.
* Validate JWT tokens before forwarding protected requests.
* Apply rate limiting using Redis.
* Propagate `X-Trace-Id` / correlation information.
* Provide health and application information through Spring Boot Actuator.
* Centralize request-level security before traffic reaches the internal services.

Authorization based on application roles is handled by the individual microservices and their endpoints.

## Technology Stack

* Java 17
* Spring Boot
* Spring Cloud Gateway
* Spring Security
* Spring Cloud Netflix Eureka Client
* Redis
* JSON Web Tokens (JWT)
* JJWT
* Spring Boot Actuator
* Docker

The main dependencies include:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.security:spring-security-test'
```

## Architecture

The API Gateway sits between external clients and the internal microservices.

```text
                         External Client
                               │
                               ▼
                       ┌───────────────┐
                       │  API Gateway  │
                       │    :9093      │
                       └───────┬───────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
        Auth Service      Product Service   Order Service
              │                │                │
              │                │                │
              └────────────────┼────────────────┘
                               │
                               ▼
                       Analytics Service
```

Service discovery is provided by Eureka.

```text
                         API Gateway
                              │
                              ▼
                           Eureka
                              │
             ┌────────────────┼────────────────┐
             ▼                ▼                ▼
        auth-service    product-service   order-service
                                             
                              │
                              ▼
                       analytics-service
```

The Gateway uses Spring Cloud LoadBalancer through Eureka service discovery to route requests using service identifiers such as:

```text
lb://auth-service
lb://product-service
lb://order-service
lb://analytics-service
```

## Public Entry Point

The Gateway runs on port:

```text
9093
```

In the Docker deployment, the Gateway is the only service with a host-to-container port mapping:

```text
9093:9093
```

The other microservices remain accessible only through the internal Docker/network infrastructure.

This prevents clients from bypassing the Gateway and directly accessing services such as `order-service`, `product-service` or `analytics-service`.

## Service Discovery

The Gateway registers itself with Eureka and retrieves the service registry.

Configuration:

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://eureka:8761/eureka}
    register-with-eureka: true
    fetch-registry: true
```

Spring Cloud Gateway discovery locator is enabled:

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

This allows the Gateway to discover services registered in Eureka and route requests through their logical service names.

## Routed Services

The Gateway currently routes requests to:

* `auth-service`
* `product-service`
* `order-service`
* `analytics-service`

The configured application routes include authentication, products, categories, packs, orders, clients, FedEx-related order operations and analytics/reporting operations.

## Routing

The Gateway uses Spring Cloud Gateway predicates to determine which service should receive each request.

Examples include:

```yaml
- id: product-service-products
  uri: lb://product-service
  predicates:
    - Path=/products/**
```

```yaml
- id: pedido-service
  uri: lb://order-service
  predicates:
    - Path=/orders/**
```

```yaml
- id: analytics-service
  uri: lb://analytics-service
  predicates:
    - Path=/analytics/**
```

Specific operations can also have dedicated routes when they require different rate-limiting rules.

For example, report generation endpoints have their own routes and rate limits.

## Authentication

The Gateway validates JWT tokens before forwarding protected requests to internal services.

The JWT secret is provided through an environment variable:

```yaml
jwt:
  secret: ${JWT_SECRET}
```

The Gateway therefore provides an initial authentication layer at the system boundary.

The microservices also validate JWT authentication themselves.

This creates an additional security boundary between external traffic and internal services.

## Authorization

Authentication and authorization are separated.

The Gateway validates whether the JWT is valid.

Role-based authorization is handled by the individual microservices and their endpoints.

For example, an endpoint can require an `ADMIN` or `USER` role according to the security configuration of the corresponding service.

Therefore, the Gateway does not replace the authorization rules implemented inside the microservices.

```text
Client
  │
  ▼
API Gateway
  │
  ├── Validate JWT
  ├── Rate Limit
  └── Route Request
          │
          ▼
     Microservice
          │
          └── Validate JWT
              Check required role
```

## Public and Protected Routes

Most application endpoints require authentication.

The main exceptions are:

* Authentication login endpoint.
* Actuator health and information endpoints.

The login route is:

```text
POST /auth/login
```

It is intentionally accessible without an existing JWT because its purpose is to obtain authentication credentials.

Application endpoints are protected by JWT authentication.

## Rate Limiting

The Gateway uses Redis-based rate limiting through Spring Cloud Gateway's `RequestRateLimiter`.

Redis configuration:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
```

The rate limiter uses different key resolvers depending on the route.

For example:

```yaml
key-resolver: "#{@userKeyResolver}"
```

and the login endpoint uses:

```yaml
key-resolver: "#{@loginKeyResolver}"
```

This allows authentication requests and authenticated user requests to be controlled independently.

## Rate Limit Configuration

General application routes use:

```yaml
replenishRate: 1
burstCapacity: 20
```

This configuration is applied to routes such as:

* `/auth/**`
* `/products/**`
* `/categories/**`
* `/packs/**`
* `/orders/**`
* `/clients/**`
* `/analytics/**`

Specific sensitive operations use a lower burst capacity.

For example, report generation routes use:

```yaml
replenishRate: 1
burstCapacity: 1
```

This applies to:

```text
POST /analytics/reports/generate/monthly
POST /analytics/reports/generate/ia/monthly
GET  /analytics/reports/generate/ia/year
```

The login endpoint also uses a stricter configuration:

```yaml
replenishRate: 1
burstCapacity: 1
```

This helps limit repeated authentication attempts.

## Request Tracing

The Gateway propagates `X-Trace-Id` / correlation information through the request flow.

This allows a request to be followed across the Gateway and the downstream microservices.

```text
Client
  │
  │ X-Trace-Id
  ▼
API Gateway
  │
  │ X-Trace-Id
  ▼
Microservice
  │
  │ X-Trace-Id
  ▼
Logs
```

This is particularly useful when investigating failures that involve multiple services.

## CORS

CORS is not configured at the API Gateway.

The current Gateway configuration does not include a CORS policy.

## Error Handling

The Gateway has centralized error handling.

If a downstream microservice is unavailable or returns an error, the Gateway returns the corresponding error to the client rather than silently hiding the failure.

This allows clients to receive information that the requested operation could not be completed.

The Gateway also provides centralized handling for Gateway-level errors.

## Downstream Service Failure

The Gateway does not currently hide unavailable downstream services behind a fallback response.

If a target microservice is unavailable, the request fails and the Gateway returns the resulting error.

For example:

```text
Client
  │
  ▼
API Gateway
  │
  ▼
Order Service
  │
  X  unavailable
  │
  ▼
Gateway Error Response
  │
  ▼
Client
```

The Gateway therefore does not pretend that a request succeeded when the destination service is unavailable.

## Monitoring

Spring Boot Actuator is enabled.

The exposed Actuator endpoints are:

```text
/actuator/health
/actuator/info
```

The health endpoint is configured to show details:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info

  endpoint:
    health:
      show-details: always
```

These endpoints are intended for service monitoring and health checks.

## Docker Networking

The Gateway is designed to be the only externally exposed application service.

```text
Internet / Client
       │
       ▼
┌─────────────────┐
│   API Gateway   │
│   9093:9093     │
└────────┬────────┘
         │
     Internal Network
         │
 ┌───────┼────────┬──────────┐
 ▼       ▼        ▼          ▼
Auth   Product   Order    Analytics
```

The internal services do not require public host port mappings.

This prevents direct external traffic from bypassing the Gateway's authentication and rate-limiting layer.

## Security Model

The security architecture uses multiple layers.

At the external boundary:

```text
Client
  │
  ▼
API Gateway
  │
  ├── JWT validation
  ├── Rate limiting
  ├── Request routing
  └── Trace propagation
  │
  ▼
Microservice
  │
  ├── JWT validation
  └── Role authorization
```

This means that internal services are not expected to rely exclusively on the Gateway for authentication.

Each service can independently validate the JWT and enforce its own authorization rules.

## Environment Variables

Sensitive and environment-specific configuration is provided through environment variables.

The Gateway currently uses variables including:

```text
JWT_SECRET
REDIS_HOST
REDIS_PORT
EUREKA_URL
```

The JWT secret is not stored directly in the application configuration.

## Reliability

The Gateway provides several mechanisms that contribute to system reliability:

* Service discovery through Eureka.
* Load-balanced routing through service identifiers.
* Redis-based rate limiting.
* Centralized authentication.
* Request tracing.
* Health monitoring through Actuator.
* Centralized error handling.

The Gateway does not currently provide a fallback implementation for unavailable downstream services.

## Testing

The project includes Spring Boot and Spring Security testing dependencies.

The current `api-gateway` does not have dedicated tests implemented yet.

## Role in the Architecture

The API Gateway is the security and routing boundary of the system.

Its primary purpose is to prevent external clients from communicating directly with the internal microservices while centralizing common concerns such as:

```text
                    API Gateway
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   Authentication   Rate Limiting     Routing
        │                │                │
        └────────────────┼────────────────┘
                         │
                    Trace ID
                         │
                         ▼
                 Internal Services
```

This keeps the internal microservices isolated from direct external traffic while providing a single controlled entry point into the application.

## Summary

`api-gateway` provides the entry point between external clients and the internal microservices.

It combines Spring Cloud Gateway, Eureka, Spring Security, JWT and Redis to provide:

* Centralized request routing.
* JWT authentication at the system boundary.
* Redis-based rate limiting.
* Eureka-based service discovery.
* Request trace propagation.
* Health monitoring.
* Centralized Gateway error handling.
* Isolation of internal microservices from direct external HTTP access.

Role-based authorization remains implemented by the individual microservices, allowing each service to enforce its own access rules according to its business responsibilities.
