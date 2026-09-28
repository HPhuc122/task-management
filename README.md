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
startup creates `users`, `categories`, and `tasks`. No demo users or passwords
are inserted. Store only encoded password hashes in `users.password_hash`.

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

These five tests verify JPA persistence and timestamps, category deletion,
owner deletion restrictions, status validation and email uniqueness. Flyway
creates the schema in the selected database; test data is rolled back.
Without `RUN_DATABASE_TESTS=true`, the database tests are skipped.
