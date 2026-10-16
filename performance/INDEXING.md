# Cursor pagination index experiment

Run from the repository root with Python 3 and Docker Engine available:

```powershell
python performance/indexing-benchmark.py
```

The runner creates a disposable PostgreSQL container with no network or published
ports, applies the six repository schema migrations using `psql`, and replaces
the sample rows with 1,000 synthetic users and 200,000 tasks (200 tasks/user).
It uses 2 CPUs, 1 GiB RAM, and a 768 MiB tmpfs data directory. It stops its own
container in `finally`; no existing Compose database or volume is accessed.
The `trust` authentication setting applies only to this container, which has no
network and contains synthetic data. No real credentials are used.

`indexing-seed.sql` replaces rows and must only be used through this runner's
fresh database. Do not execute it against a development/demo database.

The two SQL queries reproduce the predicates, ordering, selected Task columns,
and row limit of `TaskRepository` for page size 20, including the extra row used
to determine `hasNext`. They select full rows rather than just IDs, avoiding an
unrepresentative index-only scan.

The baseline removes only `idx_tasks_user_id_id_desc`; the other indexes from
V1/V3/V4 remain. The indexed condition creates the exact composite index from
`V4__add_pagination_and_cache_indexes.sql`. This isolates its contribution and
does not compare the whole V3 schema against the whole V4 schema.

After `VACUUM ANALYZE`, five paired rounds alternate condition order. Each query
has three warm-up executions before one recorded execution in each condition.
PostgreSQL chooses the plan naturally. Compare all five execution times, their
median, plan nodes, row counts, and shared buffer hits/reads. The root buffer
counts already include descendants: do not sum them across nodes.

Outputs are in `target/indexing-benchmark/` (ignored by Git): raw JSON plans,
environment/fixture metadata, representative median plans, and `summary.md`.
The script checks that results are identical in both index conditions.

These are database-only warm-cache timings on synthetic data, not API or Redis
benchmarks. Hardware load and PostgreSQL statistics affect results. The test does
not force index usage, prove all indexes are beneficial, or measure cold-disk
performance. Applying schema SQL directly also does not validate Flyway itself.
