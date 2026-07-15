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
