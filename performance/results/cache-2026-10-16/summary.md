# Redis Cache benchmark — 2026-10-16

Spring Boot 4.0.8; 3 categories; 5 VUs; 30 seconds/run; three paired rounds.
Initial warm-up: 60 seconds/variant; before every measured run: 20 seconds.
Order: OFF/ON, ON/OFF, OFF/ON. Same source except @EnableCaching in the temporary OFF copy.
Each variant: 1 CPU, 512 MiB RAM, Java heap 128–256 MiB. Same PostgreSQL and Redis services.

| Round | Cache | Requests | Average ms | P95 ms | Requests/s | Error rate | Category SQL calls | Redis hits |
|---|---|---|---|---|---|---|---|---|
| 1 | off | 4816 | 30.898 | 87.790 | 159.626 | 0% | 4816 | 0 |
| 1 | on | 5391 | 27.521 | 86.841 | 177.684 | 0% | 0 | 5391 |
| 2 | on | 5176 | 28.630 | 87.453 | 170.602 | 0% | 0 | 5176 |
| 2 | off | 5092 | 29.173 | 87.992 | 167.149 | 0% | 5092 | 0 |
| 3 | off | 8688 | 16.945 | 81.237 | 286.480 | 0% | 8688 | 0 |
| 3 | on | 10347 | 14.209 | 78.502 | 342.315 | 0% | 0 | 10347 |

Arithmetic mean of the three run averages/P95s/rates per condition; P95 is not pooled.

{
  "off": {
    "avg_ms": 25.672095530528225,
    "p95_ms": 85.67318726666666,
    "requests_per_second": 204.41828142161012,
    "error_rate": 0,
    "total_requests": 18596,
    "category_sql_calls": 18596,
    "redis_hits": 0
  },
  "on": {
    "avg_ms": 23.453326334551534,
    "p95_ms": 84.2653747,
    "requests_per_second": 230.20058865842904,
    "error_rate": 0,
    "total_requests": 20914,
    "category_sql_calls": 0,
    "redis_hits": 20914
  },
  "change_percent": {
    "avg_latency_reduction": 8.642727249663817,
    "p95_reduction": 1.6432358962958915,
    "throughput_increase": 12.612525189781465
  }
}

Category SQL calls are measured by pg_stat_statements. Authentication SQL is not included in that count.
Redis counters were sampled only during measured load, after cache warm-up: zero misses in all measured ON runs.
A zero Category SQL count does not mean zero database queries for the HTTP request.
Synthetic local closed-loop workload on three rows; limited runs, shared host/background load, CPU quotas, no production capacity claim.
Only metrics are exported from k6; setup_data and JWTs are excluded.
