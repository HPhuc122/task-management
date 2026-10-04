# Task Management API

A simple REST API built with Java and Spring Boot.

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
- manage users
- create, view, update and delete projects

An administrator can manage users and categories.


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
docker compose up --build -d
```

This builds and starts the Spring Boot API, PostgreSQL and Redis. The API is at
`http://localhost:8080` by default; set `APP_PORT` in `.env` to change the host
port. `docker compose ps` shows container health and `docker compose logs app`
shows application output. PostgreSQL and Redis publish their ports only on the
local machine. Keep `.env` private and use a unique database password outside
local development. The example JWT key is public and intended only for local/demo;
replace `JWT_SECRET` with a private Base64 key of at least 32 random bytes before
any nonlocal deployment. A missing or invalid key prevents startup. Changing the
key invalidates existing tokens. `JWT_ACCESS_TOKEN_TTL` defaults to `1h`.

Flyway applies `V1` (schema), `V2` (demo data), `V3` (pagination indexes) and
`V4` (projects) before Hibernate validates the mappings. The current `V2`
migration inserts sample users, categories and tasks in every environment.
Its password hashes are placeholders. Only the `demo` profile (without `prod`) activates two
seeded accounts with BCrypt hashes and passwords from `DEMO_USER_PASSWORD` and
`DEMO_ADMIN_PASSWORD`. Outside that profile, these accounts cannot log in or
authenticate with a previously issued JWT, even if the database was used in a
demo run. Do not reuse the demo database, passwords or JWT key in production.
Never edit a migration already applied to a database. See
`docs/architecture.md` for relationships and deletion rules.

`docker compose down` preserves database and Redis volumes. Running
`docker compose down -v` deletes those volumes and their data.

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
  startup. The log shows two `Greeter` beans, `@Primary` selection and the same
  singleton instance injected into two services. Spring creates and manages
  these beans when the application context starts; constructing one with
  `new` would bypass container injection and Spring proxies.
- The default `dev` profile logs Hibernate SQL and bound parameters. `prod`
  suppresses detailed SQL logs. Never enable bound-parameter logging for
  production data.
- Authenticated ADMIN `GET /api/categories` uses Redis cache. Creating,
  updating or deleting a category updates or invalidates its cache entries.
- Authenticated `GET /api/tasks?userId=<your-user-id>&size=1` returns a cursor
  page. Pass the returned `nextCursor` as `cursor` to request the next page. `V3` adds the
  `(user_id, id DESC)` index used by this query pattern.
- CORS origins come from `CORS_ALLOWED_ORIGINS`; the default local origins are
  `http://localhost:3000` and `http://localhost:5173`.

For integration tests, use a **separate, disposable PostgreSQL database** and
set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` and `RUN_DATABASE_TESTS=true`, then run `mvn test`. Flyway
creates the schema and seed data. Without `RUN_DATABASE_TESTS=true`, database
tests are skipped; unit and MVC tests still run.

GitHub Actions runs `mvn verify` on every push and pull request with disposable
PostgreSQL and Redis services and all integration tests enabled.

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
follow [Spring Security's JWT Resource Server support](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
