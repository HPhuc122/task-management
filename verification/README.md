# Backend report evidence

The 2026-10-16 snapshot is under results/chapter-review-2026-10-16/. It records
the base commit plus local changes, not a final committed release. It contains
sanitized summaries, actual AOP logs, database foreign keys and generated OpenAPI.
Full local logs and Surefire XML remain under ignored target/chapter-review/.
No JWT, login response, or .env contents are included in the snapshot.

To rerun with Python 3 and Docker Engine, from the repository root:

```powershell
python verification/run-chapter-review.py
python verification/run-evidence.py
docker compose -p taskflow-chapter-review -f docker-compose.clean-test.yml -f target/chapter-review/test-services.json stop
```

The verify script reads the local .env through Docker Compose without printing
its contents. It starts an independent PostgreSQL/Redis/RabbitMQ/Mailpit/MinIO
stack and applies the test storage credentials expected by application.properties.
The MinIO keys in that override are dummy test fixture values, never production
credentials. Tests run in a pinned Maven/Java 21 container on the private network,
with all three integration-test gates enabled. Sources are copied inside the
temporary build container for faster Linux filesystem access. Nothing in the
application source tree is changed. --offline requires an already populated
Maven dependency cache; the recorded run used this option and a 512 MiB test heap.
Use this project only for disposable test data, since broker tests purge queues.
Dependencies remain running after verify for the runtime evidence step; stop
them with the final command. Volumes are retained and development stacks are
not accessed.

ChapterEvidence.java is a temporary classpath-only fixture. It unpacks the built
application jar in a throwaway container, inspects TaskController's actual proxy,
and adds a controller-shaped fixture with no HTTP mappings to demonstrate
self-invocation. It then calls real login/category/missing-task HTTP endpoints,
checks their status codes, and exports OpenAPI. The fixture is not included in
the application jar and is not a production endpoint. Demo passwords for this
step match the existing integration-test fixtures and are passed in memory.

The results classify 28 unit/focused-context tests, 36 WebMvcTest cases,
4 DataJpaTest cases and 47 SpringBootTest cases: 115 total, no failures, errors
or skipped cases. Parameterized invocations count separately. WebMvc slices
mock services/dependencies; they do not prove database behavior. JPA slices
explicitly disable embedded database replacement and use real PostgreSQL 17.11.
PostgreSQL-specific JDBC locks/ON CONFLICT/RETURNING and transaction behavior
are exercised by database, idempotency and notification integration tests.
Frontend checks and test coverage percentages are not part of this run.
