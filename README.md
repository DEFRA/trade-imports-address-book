CDP Java Spring Boot backend template.

* [Install MongoDB](#install-mongodb)
* [Inspect MongoDB](#inspect-mongodb)
* [Testing](#testing)
* [Running](#running)
* [Dependabot](#dependabot)

### Docker Compose

A Docker Compose template is in [compose.yml](compose.yml).

A local environment with:

- Floci for AWS services (S3, SQS)
- Redis
- MongoDB
- This service.
- A commented out frontend example.

```bash
docker compose --profile services up --build -d
```

A more extensive setup is available
in [github.com/DEFRA/cdp-local-environment](https://github.com/DEFRA/cdp-local-environment)

### MongoDB

#### MongoDB via Docker

Run infrastructure services (MongoDB, Floci, Redis):

```bash
docker compose --profile infra up -d
```

#### MongoDB locally

Alternatively install MongoDB locally:

- Install [MongoDB](https://www.mongodb.com/docs/manual/tutorial/#installation) on your local
  machine
- Start MongoDB:

```bash
sudo mongod --dbpath ~/mongodb-cdp
```

#### MongoDB in CDP environments

In CDP environments a MongoDB instance is already set up
and the credentials exposed as enviromment variables.

### Inspect MongoDB

To inspect the Database and Collections locally:

```bash
mongosh
```

You can use the CDP Terminal to access the environments' MongoDB.

### Testing

Run the tests with:

```bash
mvn test
```

There are also application level ests run by running a full Spring Boot application backed
by [Testcontainers](https://testcontainers.com/).
These tests do not use mocking of any sort and read and write from the containerized database.

```bash
mvn clean verify
```

### Running

Run the application:

```bash
mvn spring-boot:run
```

### Soft delete (tombstones)

`DELETE /operators/{operator-id}` is a soft delete: the document is not removed, its
`status` flips to `DELETED` and `modified_at` is bumped. The tombstone stays fetchable
by id so a consumer can distinguish "the user deleted this" (200 + `DELETED`) from
"unknown / not yours" (404) — the existence check other services rely on rests on that
distinction. Tombstones are excluded from list results.

Tombstones are retained indefinitely (~1KB each). Purge / TTL of old tombstones is
deferred; there is no automatic expiry today.

### OpenAPI contract

The API contract is locked. [`docs/openapi/api-contract.locked.yaml`](docs/openapi/api-contract.locked.yaml)
is the in-repo copy of the human-authored source of truth; build to it exactly.

[`docs/openapi/operators.yml`](docs/openapi/operators.yml) is the springdoc-generated spec,
committed so downstream (frontend, other services) build against a file, not a running
service. `OperatorComplianceIT` gates it on every `mvn verify`: the build fails if the
committed file is stale against the live `/v3/api-docs`, and if the generated surface
diverges from the locked contract. Regenerate it after an intentional API change:

```bash
mvn verify -Dopenapi.generate=true -Dit.test=OperatorComplianceIT
```

`/v3/api-docs` is served in every profile; the interactive swagger-ui page is enabled
only under the `local` Spring profile.

### Indexes

The list read path is served by the `crn_status_type_created` and `org_status` indexes
(`OperatorIndexIT` pins their presence). Datasets are one user's address book today, so a
bounded scan is cheap. Any future index addition must be reassessed against real collection
size — an index that helps at hundreds of rows can hurt writes at millions.

### Declared REST deviations

The contract declares four deliberate divergences from
`docs/best-practices/rest-api/rest-api.md`. They are ruled decisions — do not "fix" them;
full rationale is in the locked contract:

- **D1** — `country` is an MDM display-name string (e.g. "United Kingdom"), not ISO 3166-1
  alpha-2 (c-004). No code↔name conversion exists anywhere.
- **D2** — pagination is `page` / `page_size`, not `offset`/`limit` or cursor
  (EUDPA-185.AC4; matches the animals-backend list convention).
- **D3** — `POST /operators` is not idempotent (no `Idempotency-Key`); an `Idempotency-Key`
  header can be added additively later.
- **D4** — no optimistic locking (no `etag` / `If-Match`); concurrent edits are
  last-write-wins. ETag + If-Match can be added additively later.

Search is server-side only via `?q=` (c-012) — a ruled locus decision, not a deviation.

### SonarCloud

Example SonarCloud configuration are available in the GitHub Action workflows.

### Dependabot

We have added an example dependabot configuration file to the repository. You can enable it by
renaming
the [.github/example.dependabot.yml](.github/dependabot.yml) to `.github/dependabot.yml`

### About the licence

The Open Government Licence (OGL) was developed by the Controller of Her Majesty's Stationery
Office (HMSO) to enable
information providers in the public sector to license the use and re-use of their information under
a common open
licence.

It is designed to encourage use and re-use of information freely and flexibly, with only a few
conditions.
