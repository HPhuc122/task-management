# Cursor index measurement — 2026-10-16

Command: `python performance/indexing-benchmark.py`.
Base commit: `0808cbff44706f1acd49d77316d0aeda2fb452ac`, with local changes.
PostgreSQL 17.11, Docker Engine 29.8.2, Linux x86_64.
Docker host reports 12 CPUs / 3,932,835,840 bytes RAM; the benchmark container
is limited to 2 CPUs / 1 GiB RAM and uses a 768 MiB tmpfs data directory.
The image digest and full settings are recorded in `target/indexing-benchmark/results.json`.

Fixture: 200,000 tasks / 1,000 users / 200 tasks per user. Both queries request
21 rows, corresponding to page size 20 plus the `hasNext` probe. Cursor: 150000;
user: 42. SQL selects full Task rows. All six repository migration SQL files
were applied by psql in a fresh database; this is not a Flyway execution test.

Five paired rounds alternate condition order. Each query has three warm-up
executions per condition per round, then one recorded EXPLAIN ANALYZE execution.
Only `idx_tasks_user_id_id_desc` changes between conditions; other indexes,
rows, and ANALYZE statistics are retained. All returned rows match across
conditions. The planner settings remain at their defaults.

| Query | Without composite index: five executions (ms) | With composite index: five executions (ms) | Median before → after (ms) |
|---|---|---|---|
| First page | 1.267, 0.753, 1.610, 0.970, 1.061 | 0.262, 0.298, 0.136, 0.220, 0.275 | 1.061 → 0.262 |
| Next page | 0.753, 1.069, 0.931, 0.902, 0.839 | 0.148, 0.390, 0.159, 0.127, 0.160 | 0.902 → 0.159 |

Every baseline plan uses `Bitmap Index Scan` on `idx_tasks_user_status`, then
`Bitmap Heap Scan`, `Sort` (top-N heapsort), and `Limit`. It retrieves 200 task
rows for user 42; the next-page predicate removes 50 before sorting 150 rows.
Every indexed plan uses `Index Scan` on `idx_tasks_user_id_id_desc` and `Limit`,
returning 21 rows without a separate Sort. Its next-page Index Cond is
`((user_id = 42) AND (id < 150000))`.

Root shared buffer hits are 208 before / 27 after for both queries in every
recorded execution. Shared reads are zero: these are warm-cache measurements.
Do not sum buffer counts across child nodes, since root counts include them.

The median SQL execution time decreased by 75.31% / 82.37% for these two
queries in this fixture. This is a synthetic warm-cache microbenchmark, not
HTTP latency, Redis performance, production capacity, or a cold-disk result.
It does not measure index storage/write overhead or other workload shapes.

`idx_tasks_due_date` is not used by these queries: they have no predicate or
ordering on due_date. Its existence does not establish a benefit for current
task cursor endpoints. On small tables, a sequential scan can also be cheaper
than using an index; the measured large-fixture baseline here is a bitmap scan,
not a sequential scan.

Raw EXPLAIN JSON for all 20 samples and four representative median plans are
retained under `target/indexing-benchmark/`. Run the script to regenerate them;
timings will vary with host load. The disposable container was stopped after
the run, and no existing Compose data was accessed.
