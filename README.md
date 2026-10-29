# Task Management API

A simple REST API built with Java 21 and Spring Boot 4.0.8.

This project is primarily created as a learning project to understand
Java OOP, Spring Boot, backend architecture, database access,
dependency injection, testing, Docker, and related backend concepts.

The business domain is intentionally simple so that the main focus
remains on understanding backend architecture and Spring Boot.


---

## 1. Project Goals

The project has two main goals.

### 1.1 Learning Goal

Understand the fundamentals behind a Spring Boot backend:

- Java OOP
- 3-layer architecture
- Spring IoC
- Dependency Injection
- REST API
- Spring Data JPA
- Hibernate
- PostgreSQL
- DTO
- Validation
- Exception Handling
- Configuration
- Profiles
- Spring Security
- JWT
- Transaction
- AOP
- Unit Testing
- Integration Testing
- Docker
- GitHub Actions


### 1.2 Project Goal

Build a simple Task Management REST API.

The system allows users to:

- create tasks
- view tasks
- update tasks
- delete tasks
- change task status
- assign categories
- create, view, update and delete projects

An administrator can manage all tasks, projects and categories. A User
management API is not implemented; account administration currently requires
database administration outside the public registration API.


---

## 2. Business Domain

The main entities are:

- User
- Task
- Category
- Project


### Task Status

- TODO
- IN_PROGRESS
- DONE


### Task Priority

- LOW
- MEDIUM
- HIGH


### User Roles

- USER
- ADMIN


### Relationships

```text
User 1 -------- N Task

Category 1 ---- N Task

User 1 -------- N Project

Project 1 ----- N Task (optional for a task)
```

## 3. Run with Docker Compose

Docker Desktop with Compose is required. Create a local `.env` from
`.env.example` (`cp .env.example .env` in a Unix shell, or
`Copy-Item .env.example .env` in PowerShell), then run from the project root:

```bash
cp .env.example .env
# Generate a key, then set JWT_SECRET in .env to the output.
openssl rand -base64 32
docker compose up --build -d
```

This builds and starts the Spring Boot API, PostgreSQL, Redis, RabbitMQ, Mailpit and MinIO. The API is at
`http://localhost:8080` by default; set `APP_PORT` in `.env` to change the host
port. `docker compose ps` shows container health and `docker compose logs app`
shows application output. Published database, cache, broker and Mailpit ports
are bound to the local machine. Keep `.env` private and use a unique database password. Set
`JWT_SECRET` to a private Base64 key of at least 32 random bytes before startup.
A missing or invalid key prevents startup. Changing the
key invalidates existing tokens. `JWT_ACCESS_TOKEN_TTL` defaults to `1h`.

Flyway applies `V1` (schema), `V2` (demo data), `V3` (projects),
`V4` (pagination/cache indexes), `V5` (idempotency/notification outbox) and
`V6` (Task attachment metadata)
before Hibernate validates the mappings. The current `V2`
migration inserts sample users, categories and tasks in every environment.
Its password hashes are placeholders. Only the `demo` profile (without `prod`) activates two
seeded accounts with BCrypt hashes and passwords from `DEMO_USER_PASSWORD` and
`DEMO_ADMIN_PASSWORD`. Outside that profile, these accounts cannot log in or
authenticate with a previously issued JWT, even if the database was used in a
demo run. Do not reuse the demo database, passwords or JWT key in production.
Never edit a migration already applied to a database. See
`docs/architecture.md` for relationships and deletion rules.

Older local databases created from the previous `phuc` branch applied `V3`
for pagination and `V4` for projects. The current `main` migration order is
`V3` projects, `V4` pagination; Flyway will reject those older databases.
To keep the old volumes untouched and start this version with new volumes,
stop the old Compose project and use a new project name:

```bash
docker compose down
docker compose -p task-management-boot4 up --build -d
```

The new project starts with a fresh database and Redis/RabbitMQ volumes.
Do not use `docker compose down -v` for the old project if its data matters.

`docker compose down` preserves PostgreSQL, Redis, RabbitMQ and MinIO volumes.
Running `docker compose down -v` deletes those volumes, including attachment bytes.

### 3.1 Reproduce a verified environment

Third-party container images in `docker-compose.yml`, `docker-compose.clean-test.yml`,
the Dockerfile and CI are pinned with `tag@sha256:digest`. The tag describes
the image family; the digest selects the fixed image content even if that tag
later changes. Both the Maven build image and Java runtime image are pinned.
The digests refer to multi-platform image manifests; use the same platform
when comparing test or benchmark results.

For a demo or report, use a clean checkout of the reviewed source commit.
Record that commit, Docker version, platform and image references along with
the test command and result:

```bash
git rev-parse HEAD
docker version --format '{{.Server.Version}}'
docker info --format '{{.OSType}}/{{.Architecture}}'
docker compose -p taskflow-clean -f docker-compose.clean-test.yml config --images
```

The independent test stack uses port 18080 and separate volumes:

```bash
docker compose -p taskflow-clean -f docker-compose.clean-test.yml up --build -d
docker compose -p taskflow-clean -f docker-compose.clean-test.yml ps -a
docker compose -p taskflow-clean -f docker-compose.clean-test.yml stop
```

Named volumes retain data between runs. For a fresh database, choose a new
test project name and stop the previous test stack before reusing port 18080.
Keep a private record of the environment settings and starting dataset used
for each experiment. Do not include passwords, JWT keys or tokens in report
screenshots or committed evidence. Benchmark comparisons also need consistent
hardware, resource limits, dataset, warm-up and load settings; pinning images
alone does not guarantee identical timings or byte-identical application builds.

To update an image, inspect its intended tag with
`docker buildx imagetools inspect <image>:<tag>`, then replace its digest in
every applicable Compose file, Dockerfile and CI reference together. Build
the application, run the relevant tests and start the independent stack
before submitting the update for review. The Dockerfile packages with
`-DskipTests`; its successful build must be accompanied by separate test results.

### 3.2 Run verification and benchmarks

With Python 3, Docker Engine and the local `.env` configured, run the complete
backend verification from the repository root:

```powershell
python verification/run-chapter-review.py
python verification/run-evidence.py
docker compose -p taskflow-chapter-review -f docker-compose.clean-test.yml -f target/chapter-review/test-services.json stop
```

This uses an independent PostgreSQL/Redis/RabbitMQ/Mailpit/MinIO stack and enables
all database, attachment and notification test groups. The JPA slice uses real
PostgreSQL with embedded database replacement disabled. MVC slices mock their
services; unit/focused-context tests have a separate scope. The recorded
2026-10-16 run passed **115 tests, with no failures, errors or skipped cases**.
See [verification instructions and limitations](verification/README.md) and the
[recorded test summary](verification/results/chapter-review-2026-10-16/test-summary.json).
The runtime evidence checks the controller's CGLIB proxy, self-invocation and
actual successful/failed HTTP calls. Full local logs are under `target/chapter-review/`.

Run the database index experiment or Redis API comparison separately:

```powershell
python performance/indexing-benchmark.py
python performance/cache-benchmark.py
python performance/summarize-cache-benchmark.py
```

The index experiment uses 200,000 synthetic tasks and 1,000 users in a disposable
database. The Redis comparison uses the three-category seed fixture and two
temporary builds differing only in `@EnableCaching`; application sources remain
unchanged. Its isolated services stop after the run. Read the fixture/resource
requirements and retained-volume behavior before repeating either experiment:
[indexing protocol](performance/INDEXING.md),
[cache protocol](performance/CACHE-BENCHMARK.md).
Results are workload-specific, not production performance guarantees.

## 4. Local test accounts and implemented features

Copying `.env.example` enables `dev,demo` and supplies **public local-only**
credentials. After `docker compose up --build -d`, use `POST /api/auth/login`
with JSON body:

| Role | Email | Password from `.env.example` | Access |
| --- | --- | --- | --- |
| USER | `phuc@example.com` | `local-demo-user-pass` | Own seeded tasks |
| ADMIN | `admin@example.com` | `local-demo-admin-pass` | Category API and all tasks |

If you already have a local `.env` with `SPRING_PROFILES_ACTIVE=dev,demo`, add
`DEMO_USER_PASSWORD` and `DEMO_ADMIN_PASSWORD` to it before restarting.

For example, send `{"email":"phuc@example.com","password":"local-demo-user-pass"}`
to `http://localhost:8080/api/auth/login`, then copy `accessToken` into the
`Authorization: Bearer <accessToken>` header. In Postman, set Authorization
type **Bearer Token** and paste the token. The response also contains the
user ID needed for cursor pagination. Change `SPRING_PROFILES_ACTIVE=prod`
and set private credentials before any production deployment. If a demo account
already has a different password or role, startup fails instead of overwriting it;
use a fresh local database or restore the expected credentials.

- The `demo` profile runs the IoC/DI demo on
  startup. The log shows two `Greeter` beans, default `@Primary` selection,
  explicit `@Qualifier("formalgreeter")` selection and the same singleton
  injected into two services. Two calls to `ObjectProvider.getObject()` return
  distinct `PrototypeNote` instances. Spring initializes non-lazy singletons
  when the context starts and prototypes when requested. Lifecycle tests verify
  `@PostConstruct` and singleton destruction; prototype destruction is not
  automatic. Constructing an object with `new` bypasses automatic container
  injection, lifecycle callbacks and Spring proxies.
- The default `dev` profile logs Hibernate SQL and bound parameters. `prod`
  suppresses detailed SQL logs. Never enable bound-parameter logging for
  production data.
- Spring AOP logs the execution time of public REST controller methods in
  milliseconds, including whether they returned or threw an exception. This
  measures controller method execution, not authentication, request parsing,
  response serialization, or the full HTTP request. Arguments, headers, tokens,
  response bodies, and exception details are not logged by the timing aspect.
- Authenticated ADMIN `GET /api/categories` uses Redis cache. Creating,
  updating or deleting a category updates or invalidates its cache entries.
  Category entries have a one-hour TTL; null values are not cached. Keys are
  `boot4::categories::all` and `boot4::categories::<id>`. Cache writes and
  evictions are deferred until the database transaction commits through
  `RedisCacheManager.transactionAware()`. A category cache hit avoids its
  repository query, but authentication can still query PostgreSQL.
- Authenticated `GET /api/tasks?userId=<your-user-id>&size=1` returns a cursor
  page. Pass the returned `nextCursor` as `cursor` to request the next page. `V4` adds the
  `(user_id, id DESC)` index used by this query pattern.
- CORS origins come from `CORS_ALLOWED_ORIGINS`; the default local origins are
  `http://localhost:3000` and `http://localhost:5173`.

For integration tests, use a **separate, disposable PostgreSQL database** and
set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` and `RUN_DATABASE_TESTS=true`, then run `mvn test`. Flyway
creates the schema and seed data. Without `RUN_DATABASE_TESTS=true`, database
tests are skipped; unit and MVC tests still run.

GitHub Actions runs `mvn verify` on every push and pull request with disposable
PostgreSQL, Redis, RabbitMQ, Mailpit and MinIO services and all integration tests enabled.

```bash
RUN_DATABASE_TESTS=true mvn test
```

In PowerShell, set `$env:RUN_DATABASE_TESTS='true'` and the PostgreSQL
environment variables before running `mvn test`.

These tests verify JPA persistence and timestamps, category/project deletion,
owner deletion restrictions, status validation, email uniqueness and HTTP CRUD
for both Task and Project. Flyway
creates the schema in the selected database. Database tests roll back their
fixtures; HTTP CRUD tests use real service transactions and delete their fixtures
after each test.
Without `RUN_DATABASE_TESTS=true`, database tests are skipped. Controller,
authentication/JWT/CORS and service ownership tests run with `mvn test` without
PostgreSQL. `SecurityIntegrationTest` additionally verifies registration, BCrypt,
login, JWT-authenticated CRUD, cross-user access rejection, rollback of rejected
updates, ADMIN access and deleted users against the disposable database. Tests
supply a public test-only JWT key; never use it for a running application.
Mockito is configured as a Java agent for Java 21 test execution.

## 5. Task and Project CRUD API

| Method | Task | Project | Success |
| --- | --- | --- | --- |
| POST | `/api/tasks` | `/api/projects` | 201 + Location |
| GET | `/api/tasks` | `/api/projects` | 200, array ordered by id |
| GET | `/api/tasks/{id}` | `/api/projects/{id}` | 200 |
| PUT | `/api/tasks/{id}` | `/api/projects/{id}` | 200 |
| DELETE | `/api/tasks/{id}` | `/api/projects/{id}` | 204 |

The plain `GET /api/tasks` returns an array. Supplying `userId` selects the
cursor route and returns `{content, nextCursor, hasNext}` instead: `userId`
is required for that route, while positive `cursor` and `size` are optional.
The default size is 20; values above 100 are capped at 100. Supplying only
`cursor` or `size` without `userId` does not select a supported list route.
Project and Category list endpoints return arrays. Search, status/priority,
project/category and overdue filters, plus Dashboard statistics, are computed
by the frontend from authorized records; they are not backend query parameters
or a dedicated Dashboard API.

All CRUD requests require `Authorization: Bearer <accessToken>`. USER sees only
its own records and must send its own `userId`; ADMIN can manage every record.
See authentication examples below to obtain `TOKEN` and `USER_ID`.

Create a project:

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Spring Boot project\",\"description\":\"Course work\",\"userId\":$USER_ID}"
```

Create a task (optionally add `categoryId` and a `projectId` you own):

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Build CRUD API\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"userId\":$USER_ID}"
```

PUT uses the same body as POST and replaces the editable fields. Task `title`
and `userId`, and Project `name` and `userId`, are required. Title/name must be
nonblank and at most 255 characters; IDs must be positive. Omitted or null
status/priority become TODO/MEDIUM; omitted optional fields become null.
Dates use ISO-8601 instants. Deleting a Project preserves its Tasks and clears
their projectId. USER can only attach tasks to its own projects; ADMIN can
assign different Task and Project owners.

Responses contain IDs of related records, never nested User entities or
password hashes, and include database-generated `createdAt`/`updatedAt`.
Invalid input returns 400, missing resources (including referenced IDs) return
404, and database constraint conflicts return 409, with ProblemDetail bodies.
Validation errors additionally contain an `errors` object keyed by field name.
Missing/invalid/expired tokens return 401 with `WWW-Authenticate: Bearer`;
forbidden access returns 403. Unexpected errors return a safe 500 response.

Source structure: `controller` handles HTTP and validation, `service` owns
transactions and business logic, `repository` provides JPA access, `dto`
defines API payloads, and `exception` handles errors centrally.

### Task attachments (MinIO/S3)

Attachment bytes are stored in a private S3-compatible bucket; PostgreSQL stores
only metadata and the object key. When running with Compose, the application
creates the `task-attachments` bucket in MinIO automatically. Its API is at `http://localhost:9000` and console at
`http://localhost:9001` by default. The local credentials and ports come from
`.env`; use private credentials in every non-local environment.
Compose uses Chainguard's maintained public MinIO image because the upstream
community project no longer publishes its prebuilt Docker Hub/Quay images.

| Method | Endpoint | Success |
| --- | --- | --- |
| POST | `/api/tasks/{taskId}/attachments` (`multipart/form-data`, part `file`) | 201 |
| GET | `/api/tasks/{taskId}/attachments` | 200 metadata array |
| GET | `/api/tasks/{taskId}/attachments/{attachmentId}/content` | 200 file bytes |
| DELETE | `/api/tasks/{taskId}/attachments/{attachmentId}` | 204 |

```bash
curl -i -X POST "http://localhost:8080/api/tasks/$TASK_ID/attachments" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./notes.pdf"
```

The default limit is 10 MB and can be changed with `ATTACHMENT_MAX_FILE_SIZE`.
Set `ATTACHMENT_MAX_REQUEST_SIZE` too when a custom file limit needs additional
multipart envelope overhead.
Task ownership rules also apply to every attachment endpoint. Deleting a Task
deletes its database rows in the transaction and removes attachment objects only
after the outer transaction commits. A rolled-back delete preserves the objects.
Successful uploads register rollback cleanup, including failures after
`saveAndFlush`. Cleanup errors are logged with the object key without masking
the database error or reporting a committed delete as failed.

PostgreSQL and MinIO do not share an atomic transaction. Cleanup is best effort:
a process crash, failed MinIO deletion, or unknown commit outcome can leave
unreferenced objects. An unknown outcome retains the object to avoid deleting a
possibly committed attachment. Reconcile logged keys against committed metadata;
durable retry/outbox and periodic orphan reconciliation remain follow-up work.

Real storage tests (no storage/repository mocks) use a disposable PostgreSQL
database and Docker MinIO. Start isolated fixtures and run:

```bash
docker run -d --name attachment-review-postgres \
  -e POSTGRES_DB=attachment_test -e POSTGRES_USER=attachment_test \
  -e POSTGRES_PASSWORD=attachment_test_only -p 127.0.0.1:15432:5432 postgres:17@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f
docker run -d --name attachment-review-minio \
  -e MINIO_ROOT_USER=test-access-key -e MINIO_ROOT_PASSWORD=test-secret-key \
  -p 127.0.0.1:19000:9000 cgr.dev/chainguard/minio:latest@sha256:2136d593beacf4461187035eca949fe49abd589d5079735025c711ca58fee0c8 server /data
until docker exec attachment-review-postgres pg_isready -U attachment_test; do sleep 1; done
curl --fail --silent --show-error --retry 30 --retry-all-errors --retry-delay 2 \
  --connect-timeout 2 --max-time 5 http://localhost:19000/minio/health/ready
POSTGRES_HOST=localhost POSTGRES_PORT=15432 POSTGRES_DB=attachment_test \
POSTGRES_USER=attachment_test POSTGRES_PASSWORD=attachment_test_only \
S3_ENDPOINT=http://localhost:19000 RUN_ATTACHMENT_TESTS=true \
mvn -B -Dtest=AttachmentStorageIntegrationTest test
docker rm -f attachment-review-postgres attachment-review-minio
```

The test-only credentials are `test-access-key` / `test-secret-key` (configured
in `src/test/resources/application.properties`); start MinIO with these values.
The suite verifies the HTTP upload → download → delete round trip, rollback of
upload and delete, metadata insertion failure, a PostgreSQL error during commit preparation, and rollback /
commit when deleting a task with multiple attachments. CI starts Docker MinIO
and sets `RUN_ATTACHMENT_TESTS=true` so this suite runs on every build.

Verified locally on 2026-10-13 with Docker PostgreSQL 17 and MinIO: all six
`AttachmentStorageIntegrationTest` cases passed. `mvn -B verify` with
`RUN_ATTACHMENT_TESTS=true` passed (60 tests executed, 34 other infrastructure
tests skipped; zero failures/errors). This confirms the tested rollback paths;
it does not cover process crashes, lost commit acknowledgements, or durable retry.

For AWS S3 or another compatible service, configure `S3_REGION`, `S3_BUCKET`,
`S3_ACCESS_KEY`, `S3_SECRET_KEY`, optional `S3_ENDPOINT`, and
`S3_PATH_STYLE_ACCESS`. When static keys are omitted, the AWS SDK default
credential chain is used (for example, an IAM role). The bucket must already
exist outside the local Compose environment.


## 6. Authentication and authorization

Interactive API documentation is available at
`http://localhost:8080/swagger-ui/index.html`; the OpenAPI JSON is at
`http://localhost:8080/v3/api-docs`. Both documentation endpoints are public.
In Swagger UI, call `POST /api/auth/login` or `POST /api/auth/register`, copy
`accessToken` from the response, then click **Authorize** and enter the token
for protected operations. Swagger UI adds the `Bearer` prefix to the request.
The login and registration operations do not require a token. Use the port set
by `APP_PORT` instead of `8080` if you changed the Docker Compose default.

| Method | Endpoint | Access | Success |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | 201 + token and user |
| POST | `/api/auth/login` | Public | 200 + token and user |
| GET | `/api/auth/me` | Bearer token | 200 + id, email, role |

Register (choose your own password; this is a local example):

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"learner@example.com","password":"change-this-password"}'
```

Login uses the same email/password body at `/api/auth/login`. Both return:

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {"id": 4, "email": "learner@example.com", "role": "USER"}
}
```

Set `TOKEN` to `accessToken` and `USER_ID` to `user.id` from your own response:

```bash
TOKEN='<accessToken from response>'
USER_ID=4 # Replace with user.id from response.
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN"
```

Email must be valid and at most 254 characters. Email comparison remains
case-sensitive, matching the existing DB schema. Registration passwords must
be nonblank, at least 8 characters and at most 72 UTF-8 bytes (BCrypt's limit).
Passwords are not trimmed. Duplicate email returns 409, invalid credentials 401.
Registration always creates USER; client-supplied roles cannot grant ADMIN.

USER can list/read/update/delete only owned Tasks and Projects; creating or
updating with another `userId`, changing ownership, or linking another user's
Project returns 403. ADMIN can manage all Tasks and Projects. User/category
routes require ADMIN; category CRUD is implemented, user CRUD is not.

To provision an ADMIN for local learning, first register your own account, then
promote that specific account through a trusted DB connection:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'learner@example.com';
```

Each authenticated request loads the user's current role from the DB. Role
changes take effect immediately and tokens for deleted users are rejected.
The API is stateless and uses Bearer headers only, without login sessions or
cookies. JWT uses HS256 and validates its signature, issuer, expiry and user ID.
There is no refresh-token or logout endpoint: discard the token on the client
and log in again after expiry; an already-issued token remains valid until it
expires unless its user is deleted or the signing key is changed.

CORS uses `CORS_ALLOWED_ORIGINS` (comma-separated exact origins). Dev defaults
to `http://localhost:3000,http://localhost:5173`; prod allows no cross-origin
requests by default. Preflight is handled before authentication. CSRF is disabled
for the stateless Bearer API. Use HTTPS outside local development.

Errors use `application/problem+json`, for example:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/tasks",
  "errors": {"title": "must not be blank"}
}
```

Request DTOs and positive path IDs are validated before the service runs.
Responses never expose passwords, SQL errors or stack traces. Implementations
follow [Spring Security's JWT Resource Server support](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html).

## 7. RabbitMQ email notifications and idempotency

Creating a Task now queues an email to its owner's email address. The HTTP
request only writes PostgreSQL; it does not wait for RabbitMQ or SMTP. Task
updates/deletes and Project/Category operations do not send email in this scope.

```text
POST /api/tasks + Idempotency-Key
  -> one DB transaction: key + Task + notification_outbox
  -> outbox worker -> RabbitMQ -> email consumer -> SMTP (Mailpit locally)
```

Start the local stack after setting `JWT_SECRET` in your existing `.env`:

```bash
docker compose up --build -d
```

Compose enables notifications and starts RabbitMQ and Mailpit in addition to
PostgreSQL/Redis/API. Mailpit captures mail locally; no mail is delivered to real
inboxes. Open `http://localhost:8025` to read mail. RabbitMQ management is at
`http://localhost:15672` (local defaults: `task_app` / `local-rabbit-password`).
Use private broker credentials outside development. RabbitMQ data has a named
volume and stable hostname so durable queues survive container recreation;
`docker compose down -v` deletes this data. Mailpit messages are temporary.

To run the API with Maven, start infrastructure only and use localhost hosts:

```bash
docker compose up -d postgres redis rabbitmq mailpit
set -a
source .env
set +a
export NOTIFICATIONS_ENABLED=true
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-local-rabbit-password}"
export RABBITMQ_HOST=localhost SMTP_HOST=localhost SMTP_PORT=1025
mvn spring-boot:run
```

Stop any existing API on port 8080 before starting another instance.
`NOTIFICATIONS_ENABLED` defaults to false for Maven and true in Compose.
When disabled, Task creation still records outbox events; enabling notifications
later sends that backlog. For a real SMTP server set `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true` and `MAIL_FROM`.
Connection/read/write timeouts are 5 seconds. SMTP acceptance means the server
accepted the message, not that the recipient has read or received it in an inbox.

### Prevent duplicate Task creation

Obtain `TOKEN` and `USER_ID` using section 6, then run this request twice:

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: task-demo-001' \
  -d "{\"title\":\"Test RabbitMQ mail\",\"userId\":$USER_ID}"
```

Both calls return `201` with the original response body, task ID and Location;
only one Task and one email event are created, even for concurrent requests.
Changing the title with the same key returns `409` ProblemDetail. Use a new key
for a new logical operation. Without a key every POST creates a new Task.

Keys allow 1–128 ASCII letters/digits, `.`, `_`, `-`; invalid keys return 400.
Scope is authenticated actor + CREATE_TASK. Identical keys from different users
do not collide. The request hash is based on parsed DTO fields, so JSON field
order does not matter; changing a field value (including null versus an explicit
default) conflicts. Replay rechecks current ownership and returns 403 after a
transfer to another owner, or 404 after deletion. Stored responses are snapshots,
so later edits do not change a replayed creation response. Failed/rolled-back
requests do not reserve a key. Keys do not expire automatically.

### Reliability and failed messages

Migration `V5` adds `api_idempotency` and `notification_outbox`. Key claims use
PostgreSQL uniqueness, not in-memory locks. Key, Task and outbox writes share one
transaction, including rollback. Outbox rows intentionally retain snapshots after
Task/user deletion. Delivery uses the owner email captured when the Task was created.

The publisher locks pending rows with `FOR UPDATE SKIP LOCKED` and publishes a
persistent UUID message. Publisher confirms and mandatory returns must indicate
success before setting `published_at`. Failure retries with exponential backoff
from 2 seconds to 5 minutes, without a retry limit. Broker outages do not lose
committed events. Each poll handles at most 20 events (default delay 2 seconds).

Queue `task.notifications.email` is durable/quorum. The consumer locks the event
row, sends mail, records `sent_at`, and then ACKs. Redelivery of a committed event
is a no-op. SMTP failures leave `sent_at` empty and get up to 3 attempts before
dead-lettering into `task.notifications.email.dlq`. Both queues are declared by
the application when notifications are enabled. Dead-lettering uses RabbitMQ's
at-least-once quorum strategy.

After fixing SMTP configuration, republish a DLQ message to exchange
`task.notifications`, routing key `task.created`, preserving its original UUID
body. Do not invent a new UUID: deduplication uses the event ID. Use the RabbitMQ
management UI to inspect the DLQ. A repaired message whose `sent_at` is already
set will be acknowledged without sending again.

**Limit:** SMTP and PostgreSQL cannot commit atomically. If the process stops
after SMTP accepts mail but before `sent_at` commits, retry may send a duplicate.
This is at-least-once processing with deduplication, not exactly-once email.
Exactly-once delivery requires a mail provider offering its own idempotency key.
No automatic key/outbox cleanup is implemented; removing records removes their
deduplication history. Monitor unsent events, publishing attempts and the DLQ.

### Automated tests

- `mvn test`: unit/MVC tests without infrastructure.
- `RUN_DATABASE_TESTS=true mvn test`: also tests concurrent HTTP retries,
  cross-actor key scope, rollback, concurrent consumers and broker/SMTP failures
  on a disposable PostgreSQL database. Broker and SMTP are mocked for these cases.
- `RUN_DATABASE_TESTS=true RUN_NOTIFICATION_TESTS=true mvn clean verify`: also
  checks the real HTTP -> outbox -> RabbitMQ -> SMTP flow, reads the email from
  Mailpit, verifies duplicate delivery, and tests DLQ replay. Configure disposable
  PostgreSQL, a dedicated RabbitMQ vhost (`RABBITMQ_VHOST`) and local Mailpit first.
  This test purges its notification queues; never point it at production or a
  shared application vhost. `MAILPIT_API` defaults to `http://localhost:8025`.

GitHub Actions provisions disposable PostgreSQL, Redis, RabbitMQ and Mailpit and
runs all of these tests. See [Spring AMQP publisher confirms and returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html)
and [Mailpit's local SMTP behavior](https://mailpit.axllent.org/docs/usage/sending-messages/).

## Frontend demo

The React/Vite frontend runs separately from Docker Compose. With Node.js
22.12+ and npm installed, start the backend first, then run:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`;
if `APP_PORT` differs, set `API_PROXY_TARGET` in `frontend/.env.local` to the
backend origin. Never put secrets in `VITE_*` variables. The interface includes
authentication, Task/Project CRUD, ADMIN Category CRUD, Dashboard, filtering,
cursor navigation and attachment upload/download/preview.

From `frontend`, run `npm run lint`, `npm test` and `npm run build` to perform
the same frontend checks configured in CI. UI tests use fixtures and mocks;
they do not replace backend integration tests or a real browser/API demo.
See [frontend setup, API boundaries and demo instructions](frontend/README.md).
