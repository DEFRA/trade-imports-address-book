# trade-imports-address-book

Org-scoped address book API for EUDP Live Animals (EUDPA-58). A Java 25 / Spring Boot 3.5
service backed by MongoDB. Each organisation owns a flat list of addresses (the Standard Address
Block); addresses are created, listed, searched, updated, and soft-deleted via REST.

* [Prerequisites](#prerequisites)
* [Running the local stack](#running-the-local-stack)
* [Running natively](#running-natively)
* [Security and trust boundary](#security-and-trust-boundary)
* [API overview](#api-overview)
* [MongoDB](#mongodb)
* [Testing](#testing)
* [OpenAPI contract](#openapi-contract)
* [Soft delete (tombstones)](#soft-delete-tombstones)
* [Indexes](#indexes)
* [Licence](#licence)

## Prerequisites

- **Java 25** (Corretto recommended — matches CI)
- **Maven 3.9+**
- **Docker** (for Testcontainers integration tests, repo `compose.yml`, or the workspace stack)

## Running the local stack

### Repo Docker Compose (recommended)

This repo includes a standalone [compose.yml](compose.yml) with Floci, MongoDB, and the service.
The compose file builds the Dockerfile `dev-run` stage and bind-mounts `./src` for hot reload via
[docker/dev-run.sh](docker/dev-run.sh):

```bash
docker compose up --build -d
```

The service listens on **http://localhost:8089**. Health check: `GET /health`.

Interactive Swagger UI is available at **http://localhost:8089/swagger-ui.html** when
`SPRING_PROFILES_ACTIVE=local` (set by default in `compose.yml`).

### Workspace stack (planned)

Full EUDP stack integration — including this service alongside ins-frontend and the other stack
services — is planned in
[DEFRA/trade-imports-animals-workspace](https://github.com/DEFRA/trade-imports-animals-workspace).
Until that lands, use repo Docker Compose above.

When available, the workspace commands will be:

```bash
# from the workspace root
./scripts/stack/run-stack.sh                              # published images (:latest)
./scripts/stack/run-stack.sh -b feat/EUDPA-58-address-book  # branch-tagged images where available
./scripts/stack/run-stack.sh -d                           # build from local source under repos/
./scripts/stack/run-stack.sh -e trade-imports-address-book  # run this service natively instead
./scripts/stack/stop-stack.sh                             # tear down and wipe volumes
```

This service will run on **port 8089** in the stack. The Dockerfile `dev-run` stage is intended
for workspace `-d` mode hot reload; recreate the container after `pom.xml` dependency changes.

To run only the infrastructure this service needs (MongoDB + Floci):

```bash
./scripts/stack/run-stack.sh --profile database --profile infrastructure
```

## Running natively

Start MongoDB (see [MongoDB](#mongodb)), then:

```bash
export SPRING_PROFILES_ACTIVE=local
export MONGO_URI=mongodb://localhost:27017
export MONGO_DATABASE=trade-imports-address-book

mvn spring-boot:run
```

Or use the dev Dockerfile stage for hot reload against a bind-mounted `src/` tree — see
[docker/dev-run.sh](docker/dev-run.sh).

## Security and trust boundary

This service has **no Spring Security layer** and does not validate JWTs or API keys. Tenant
isolation relies entirely on a trusted-forwarded-header contract:

| Responsibility | Owner |
| --- | --- |
| Authenticate the caller (Defra ID / OIDC) | CDP ingress or calling BFF (e.g. ins-frontend) |
| Strip client-supplied `Trade-Imports-Organisation-Id` | Same upstream hop |
| Set `Trade-Imports-Organisation-Id` from the verified session | Same upstream hop |
| Block direct pod/port access without passing through the gateway | CDP network policy |

`Trade-Imports-Organisation-Id` is the tenant key for every operation. The
[`IdentityHeaderFilter`](src/main/java/uk/gov/defra/trade/imports/addressbook/filter/IdentityHeaderFilter.java)
reads this header on every `/organisation/**` request and scopes all reads and writes to that
organisation id.

**Filter behaviour**

- Missing, blank or malformed header → **400** bad-request
- Header value does not match path `{orgId}` → **404** (no cross-org existence disclosure)

**Production requirement:** callers must not reach this service without the upstream hop above.
Any direct HTTP client that can hit the service port can read, create, update or delete any
organisation's address data by setting one header. Document this boundary in runbooks and enforce
it with ingress/network policy — the service cannot defend itself against a trusted-network
violation without adding a resource server (deferred for this ticket).

## API overview

All routes under `/organisation/{orgId}/addresses` require the trusted identity header
`Trade-Imports-Organisation-Id`. The path `orgId` must match the header value; a mismatch
returns **404** (no existence disclosure).

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/organisation/{orgId}/addresses` | Paginated list of ACTIVE addresses (newest first) |
| `POST` | `/organisation/{orgId}/addresses` | Create an address |
| `GET` | `/organisation/{orgId}/addresses/{operator-id}` | Fetch one address (tombstones included) |
| `PUT` | `/organisation/{orgId}/addresses/{operator-id}` | Full replace of mutable fields |
| `DELETE` | `/organisation/{orgId}/addresses/{operator-id}` | Soft delete (idempotent 204) |

**List query parameters**

| Parameter | Description |
| --- | --- |
| `page` | 1-based page number (default `1`) |
| `q` | Case-insensitive partial search over `name`, `townOrCity`, and `postcode` |
| `countryCode` | When combined with `q`, matches addresses where **either** the search term appears in `name`/`townOrCity`/`postcode` **or** the stored `countryCode` equals this value (OR semantics, not AND) |

**Wire conventions**

- JSON properties are **camelCase** (e.g. `addressLine1`, `countryCode`, `pageSize`).
- `countryCode` is stored and returned as a **2-character ISO code**; display names come from
  reference-data / the frontend.
- List responses are a top-level object (`items`, `page`, `pageSize`, `totalItems`, `totalPages`),
  never a bare array.
- Soft-deleted addresses expose `deleted: true` on read; the internal `status` enum is not on the wire.
- Validation failures return **400** `application/problem+json` with a camelCase `errors` map.
- Page size is server-configured (default **25** via `address-book.list.page-size`), not a request parameter.

**Example — create an address**

```bash
curl -s -X POST "http://localhost:8089/organisation/{orgId}/addresses" \
  -H "Content-Type: application/json" \
  -H "Trade-Imports-Organisation-Id: {orgId}" \
  -d '{
    "name": "Highland Livestock Ltd",
    "addressLine1": "14 Drover'\''s Way",
    "townOrCity": "Inverness",
    "postcode": "IV2 3JH",
    "countryCode": "GB",
    "phone": "+44 1463 234567",
    "email": "exports@example.com"
  }'
```

## MongoDB

**Database:** `trade-imports-address-book` (override with `MONGO_DATABASE`)  
**Collection:** `addresses`

### Via Docker Compose or workspace stack

MongoDB is provided by compose / the workspace `database` profile on port **27017**.

### Locally installed MongoDB

Install [MongoDB](https://www.mongodb.com/docs/manual/tutorial/installation/) and start it:

```bash
sudo mongod --dbpath ~/mongodb-cdp
```

Set `MONGO_URI=mongodb://localhost:27017` when running the service.

### CDP environments

MongoDB credentials are supplied as environment variables by the CDP platform.

### Inspect MongoDB

```bash
mongosh
use trade-imports-address-book
db.addresses.find().pretty()
```

## Testing

Unit tests:

```bash
mvn test
```

Full build including integration tests (Testcontainers MongoDB — no mocks):

```bash
mvn clean verify
```

Integration tests cover CRUD, org scoping, soft delete, search, pagination, RFC 9457 error
shapes, and OpenAPI contract compliance (`OperatorComplianceIT`).

## OpenAPI contract

The API surface is locked and tested on every `mvn verify`:

| File | Role |
| --- | --- |
| [`docs/openapi/api-contract.locked.yaml`](docs/openapi/api-contract.locked.yaml) | Human-authored source of truth (paths, operationIds, 400 `anyOf` shapes) |
| [`docs/openapi/operators.yml`](docs/openapi/operators.yml) | springdoc-generated spec, committed for downstream consumers |

`OperatorComplianceIT` fails the build if `operators.yml` is stale against live `/v3/api-docs`, or
if paths, HTTP methods, operationIds, or component schema property names diverge from the locked contract.

Regenerate the committed artifact after an intentional API change:

```bash
mvn verify -Dopenapi.generate=true -Dit.test=OperatorComplianceIT
```

`/v3/api-docs` and Swagger UI are enabled only under the `local` profile
(see [application-local.yml](src/main/resources/application-local.yml)); they are disabled in the
default configuration.

## Soft delete (tombstones)

`DELETE /organisation/{orgId}/addresses/{operator-id}` is a **soft delete**: the document stays in MongoDB,
its internal `status` flips to `DELETED`, and `modifiedAt` is bumped. The tombstone remains
fetchable by id with `deleted: true` on the wire.

- A **404** means unknown id or an id outside the caller's organisation — it is **not** a deletion signal.
- Only a **200** response with `deleted: true` means the user deleted this address.
- Tombstones are excluded from list results.
- Deleting an already-deleted address is idempotent (**204**, no state change).

Tombstones are retained indefinitely (~1 KB each). Automatic purge / TTL is deferred.

## Indexes

The list read path is served by the `org_status_created` compound index on
`{ organisationId, status, createdAt }` (`OperatorIndexIT` pins its presence). Datasets are one
user's address book today (tens to hundreds of rows), so a bounded scan is acceptable. Reassess any
future index against real collection size before adding it.

## Licence

This code is licensed under the [Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/).
