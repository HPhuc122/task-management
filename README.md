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

## 3. Run the database and application

Requires Java 21, Maven and Docker. From the project root:

```bash
cp .env.example .env
# Generate a key, then set JWT_SECRET in .env to the output (keep it private).
openssl rand -base64 32
docker compose up -d
# Export the same variables for Spring Boot (Compose loads .env automatically).
set -a
source .env
set +a
mvn spring-boot:run
```

Use a private password in `.env` outside local development. Spring Boot runs
Flyway migrations before Hibernate validates the entity mappings. The first
startup creates `users`, `categories`, `tasks`, and `projects`. V2 inserts demo
users, categories and tasks; its placeholder password hashes are for demo data.
These demo users cannot log in. Register a new account through `/api/auth/register`;
the API stores a BCrypt hash in `users.password_hash`.

`JWT_SECRET` is required in every profile and must be Base64 encoding of at least
32 random bytes. Startup fails for a missing/invalid key. `JWT_ACCESS_TOKEN_TTL`
defaults to `1h` (Spring duration format, minimum `1s`). Changing the secret
invalidates existing tokens; keep the same private key across application restarts.

Schema: `src/main/resources/db/migration/V1__create_task_management_schema.sql`.
For future schema changes, add a new migration (V2, V3, ...); do not edit an
already applied migration. See `docs/architecture.md` for relationships and
deletion rules. Email and category uniqueness currently use case-sensitive
PostgreSQL comparison.

`docker compose down` preserves database data.
`docker compose down -v` deletes the database volume and all its data.

## 4. Database integration tests

Use a separate, disposable PostgreSQL database and export its connection
variables (`POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`). Then run:

```bash
RUN_DATABASE_TESTS=true mvn test
```

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
routes are reserved for ADMIN, but their CRUD endpoints are not implemented.

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
