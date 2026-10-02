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
Store only encoded password hashes for real users in `users.password_hash`.

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
Without `RUN_DATABASE_TESTS=true`, the database tests are skipped; controller
validation/error tests still run with `mvn test` and do not require PostgreSQL.

## 5. Task and Project CRUD API

| Method | Task | Project | Success |
| --- | --- | --- | --- |
| POST | `/api/tasks` | `/api/projects` | 201 + Location |
| GET | `/api/tasks` | `/api/projects` | 200, array ordered by id |
| GET | `/api/tasks/{id}` | `/api/projects/{id}` | 200 |
| PUT | `/api/tasks/{id}` | `/api/projects/{id}` | 200 |
| DELETE | `/api/tasks/{id}` | `/api/projects/{id}` | 204 |

Create a project (use an existing user ID):

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spring Boot project","description":"Course work","userId":1}'
```

Create a task (replace projectId/categoryId with existing IDs, or omit them):

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Build CRUD API","description":"Controller - Service - JPA","status":"TODO","priority":"HIGH","dueDate":"2027-01-01T00:00:00Z","userId":1,"categoryId":1,"projectId":1}'
```

PUT uses the same body as POST and replaces the editable fields. Task `title`
and `userId`, and Project `name` and `userId`, are required. Title/name must be
nonblank and at most 255 characters; IDs must be positive. Omitted or null
status/priority become TODO/MEDIUM; omitted optional fields become null.
Dates use ISO-8601 instants. Deleting a Project preserves its Tasks and clears
their projectId. A Task owner may differ from its Project owner.

Responses contain IDs of related records, never nested User entities or
password hashes, and include database-generated `createdAt`/`updatedAt`.
Invalid input returns 400, missing resources (including referenced IDs) return
404, and database constraint conflicts return 409, with ProblemDetail bodies.
Validation errors additionally contain an `errors` object keyed by field name.
Authentication and authorization have not been implemented yet.

Source structure: `controller` handles HTTP and validation, `service` owns
transactions and business logic, `repository` provides JPA access, `dto`
defines API payloads, and `exception` handles errors centrally.
