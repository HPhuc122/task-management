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

An administrator can manage users and categories.


---

## 2. Business Domain

The main entities are:

- User
- Task
- Category


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
local development.

Flyway applies `V1` (schema), `V2` (demo data) and `V3` (pagination indexes)
before Hibernate validates the mappings. The current `V2` migration inserts
sample users, categories and tasks in every environment. Its password hashes
are placeholders, so these users are **not usable login accounts**; the project
does not yet implement login. Do not treat them as production credentials.
Never edit a migration already applied to a database. See
`docs/architecture.md` for relationships and deletion rules.

`docker compose down` preserves database and Redis volumes. Running
`docker compose down -v` deletes those volumes and their data.

## 4. Verify the implemented features

- Set `SPRING_PROFILES_ACTIVE=dev,demo` in `.env` to run the IoC/DI demo on
  startup. The log shows two `Greeter` beans, `@Primary` selection and the same
  singleton instance injected into two services. Spring creates and manages
  these beans when the application context starts; constructing one with
  `new` would bypass container injection and Spring proxies.
- The default `dev` profile logs Hibernate SQL and bound parameters. `prod`
  suppresses detailed SQL logs. Never enable bound-parameter logging for
  production data.
- `GET /api/categories` uses Redis cache. Creating, updating or deleting a
  category updates or invalidates its cache entries.
- `GET /api/tasks?userId=1&size=1` returns a cursor page. Pass the returned
  `nextCursor` as `cursor` to request the next page. `V3` adds the
  `(user_id, id DESC)` index used by this query pattern.
- CORS origins come from `CORS_ALLOWED_ORIGINS`; the default local origins are
  `http://localhost:3000` and `http://localhost:5173`.

For integration tests, use a **separate, disposable PostgreSQL database** and
set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` and `RUN_DATABASE_TESTS=true`, then run `mvn test`. Flyway
creates its schema and seed data. The 5 database mapping tests and 2 API tests
cover persistence, constraints, cursor pagination and CORS. One IoC/DI unit
test runs without the database flag. Without
`RUN_DATABASE_TESTS=true`, these integration tests are skipped.
