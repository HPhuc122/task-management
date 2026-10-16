# Redis Cache experiment

Requirements: Python 3.9+, Docker Engine, and the local `.env` described by README.
Run from the repository root:

```powershell
python performance/cache-benchmark.py
python performance/summarize-cache-benchmark.py
```

This builds two temporary copies under `target/cache-benchmark/`, using the same
source and pinned Java 21/Maven images. The OFF copy removes only `@EnableCaching`
from RedisCacheConfig. The repository's application sources remain unchanged.
`spring.cache.type=none` alone would not turn off the explicit RedisCacheManager.

Dependencies use the independent Compose project `taskflow-cache-benchmark`.
Its PostgreSQL is started with pg_stat_statements enabled. Ports 18081 and 18082
are used for readiness checks. The applications share this project's PostgreSQL,
Redis, and MinIO, and have identical limits: 1 CPU, 512 MiB RAM, 128–256 MiB heap.
No development database or Redis is accessed. Services stop in `finally`; the
Compose test volumes remain available. Use a fresh project volume state when
reproducing the original three-category seed fixture; do not mix in real data.

Each variant gets 60 seconds of initial warm-up. Three paired rounds use order
OFF/ON, ON/OFF, OFF/ON, with 20 seconds of warm-up before every 30-second measured
run. k6 uses five closed-loop VUs against GET /api/categories. The app is not
restarted between rounds. Category SQL is counted with pg_stat_statements;
Redis hits/misses and key existence independently confirm the cache condition.
Only one variant receives measured load at a time; the other remains idle.

Credentials are resolved in memory and passed to Docker; k6 exports metrics
only. Its JWT-bearing setup_data is omitted from result JSON. Do not add a raw
`--summary-export`, which can include setup_data. Build logs, temporary sources,
and binaries stay under ignored target/. Metadata does not contain environment
secrets. Existing development containers remain running, so background host
load is a limitation of the experiment.

Current raw/summarized results are in target/cache-benchmark/. The sanitized
2026-10-16 snapshot is in results/cache-2026-10-16/. Inspect all individual runs:
despite warm-up, both conditions sped up markedly in round three. Arithmetic
means are descriptive, not an isolated causal estimate of Redis's speedup.
P95 is the mean of three per-run percentiles, not a pooled percentile.

The dataset has three categories. SQL avoidance is verified for Category reads;
authentication can still query PostgreSQL. All recorded cache hits are measured
after warming, and do not describe cold-cache behavior. The test does not
establish production capacity or behavior during Redis failure/concurrent writes.
