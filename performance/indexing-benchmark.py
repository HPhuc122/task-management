"""Measure V4's cursor index in a newly created, isolated PostgreSQL container."""
import argparse
import datetime
import json
from pathlib import Path
import statistics
import subprocess
import time
import uuid

IMAGE = "postgres:17@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f"
ROOT = Path(__file__).resolve().parents[1]
QUERIES = {
    "first_page": "SELECT t.* FROM tasks t WHERE t.user_id = 42 ORDER BY t.id DESC LIMIT 21",
    "next_page": "SELECT t.* FROM tasks t WHERE t.user_id = 42 AND t.id < 150000 ORDER BY t.id DESC LIMIT 21",
}


def command(*args, input_text=None):
    result = subprocess.run(args, input=input_text, text=True, encoding="utf-8",
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f"Command failed: {args[0]}\n{result.stderr}")
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "target" / "indexing-benchmark")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # The runner creates its own random container, never accepts an existing DB.
    container = "taskflow-index-" + uuid.uuid4().hex[:10]
    created = False

    def sql(text):
        return command("docker", "exec", "-i", container, "psql", "-X", "-q", "-A", "-t",
                       "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "index_benchmark",
                       input_text=text)

    def explain(query):
        return json.loads(sql("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query))[0]

    def set_index(enabled):
        if enabled:
            sql(index_ddl)
        else:
            sql("DROP INDEX IF EXISTS idx_tasks_user_id_id_desc;")

    try:
        command("docker", "run", "-d", "--rm", "--name", container,
                "--network", "none", "--cpus", "2", "--memory", "1g", "--shm-size", "256m",
                "--tmpfs", "/var/lib/postgresql/data:rw,size=768m",
                "-e", "POSTGRES_DB=index_benchmark", "-e", "POSTGRES_HOST_AUTH_METHOD=trust", IMAGE)
        created = True
        for attempt in range(60):
            try:
                sql("SELECT 1;")
                break
            except RuntimeError:
                if attempt == 59:
                    raise
                time.sleep(1)

        # Apply the repository's actual schema, including all six migrations.
        migration_dir = ROOT / "src/main/resources/db/migration"
        migrations = sorted(migration_dir.glob("V*__*.sql"),
                            key=lambda p: int(p.name.split("__")[0][1:]))
        for migration in migrations:
            sql(migration.read_text(encoding="utf-8-sig"))
        v4 = migration_dir / "V4__add_pagination_and_cache_indexes.sql"
        index_ddl = next(line for line in v4.read_text(encoding="utf-8").splitlines()
                         if line.startswith("CREATE INDEX idx_tasks_user_id_id_desc "))
        sql((ROOT / "performance/indexing-seed.sql").read_text(encoding="utf-8"))

        metadata = {
            "recorded_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "source_commit": command("git", "-C", str(ROOT), "rev-parse", "HEAD"),
            "source_has_local_changes": bool(command("git", "-C", str(ROOT), "status", "--porcelain")),
            "image": IMAGE,
            "postgres": sql("SELECT version();"),
            "docker_engine": command("docker", "version", "--format", "{{.Server.Version}}"),
            "docker_host": command("docker", "info", "--format",
                                   "{{.OSType}}/{{.Architecture}} CPUs={{.NCPU}} RAM={{.MemTotal}}"),
            "container_limits": {"cpus": 2, "memory": "1 GiB", "storage": "tmpfs 768 MiB"},
            "postgres_settings": dict(line.split("|", 1) for line in sql(
                "SELECT name, setting FROM pg_settings WHERE name IN "
                "('shared_buffers','work_mem','random_page_cost','effective_cache_size',"
                "'enable_seqscan','enable_indexscan','enable_bitmapscan','max_parallel_workers_per_gather') "
                "ORDER BY name;").splitlines()),
            "fixture": {"users": int(sql("SELECT count(*) FROM users;")),
                        "tasks": int(sql("SELECT count(*) FROM tasks;")),
                        "tasks_for_user_42": int(sql("SELECT count(*) FROM tasks WHERE user_id=42;"))},
            "migrations": [p.name for p in migrations],
            "index_ddl": index_ddl,
            "indexes_with_v4": sql("SELECT indexname, indexdef FROM pg_indexes WHERE tablename='tasks' ORDER BY indexname;"),
            "method": "5 paired rounds, alternating condition order; 3 warm-up executions per query/condition/round; default planner; same ANALYZE statistics",
            "queries": QUERIES,
        }
        samples = []
        expected_rows = {}
        # V4 is already installed: remove it before the first baseline.
        set_index(False)
        for round_number in range(1, 6):
            conditions = (False, True) if round_number % 2 else (True, False)
            for enabled in conditions:
                # Handle adjacent rounds that end/start in the same condition.
                sql("DROP INDEX IF EXISTS idx_tasks_user_id_id_desc;")
                if enabled:
                    set_index(True)
                for name, query in QUERIES.items():
                    rows = sql(query)
                    if name in expected_rows and rows != expected_rows[name]:
                        raise RuntimeError("Query results changed between index conditions")
                    expected_rows[name] = rows
                    for _ in range(3):
                        explain(query)
                    samples.append({"round": round_number, "query": name,
                                    "condition": "with_index" if enabled else "without_index",
                                    "explain": explain(query)})
            print(f"Completed paired round {round_number}/5", flush=True)

        results = {"metadata": metadata, "samples": samples}
        (output / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
        report = ["# Cursor index benchmark", "", "See results.json for raw EXPLAIN (ANALYZE, BUFFERS) plans and metadata.",
                  "", "200,000 tasks / 1,000 users; user 42 owns 200 tasks; LIMIT 21 (page size 20 + hasNext).",
                  "", "| Query | Condition | Execution times (ms) | Median (ms) | Root shared hit blocks (median) |",
                  "|---|---|---|---|---|", ]
        for name in QUERIES:
            for condition in ("without_index", "with_index"):
                plans = [s["explain"] for s in samples if s["query"] == name and s["condition"] == condition]
                times = [p["Execution Time"] for p in plans]
                hits = [p["Plan"].get("Shared Hit Blocks", 0) for p in plans]
                report.append(f"| {name} | {condition} | {', '.join(map(str, times))} | {statistics.median(times):.3f} | {statistics.median(hits)} |")
                representative = sorted(plans, key=lambda p: p["Execution Time"])[len(plans)//2]
                (output / f"{name}-{condition}-median.json").write_text(
                    json.dumps(representative, indent=2), encoding="utf-8")
        report += ["", "Database query timings only; excludes HTTP, Hibernate mapping, authentication, and Redis.",
                   "Warm-cache synthetic microbenchmark on tmpfs; not a production latency or cold-disk estimate.",
                   "Other indexes remain present; no enable_seqscan/indexscan settings were changed.",
                   "Schema SQL was applied by psql for this fixture, not by Flyway; this run does not validate Flyway."]
        (output / "summary.md").write_text("\n".join(report) + "\n", encoding="utf-8")
        print("\n".join(report), flush=True)
    finally:
        if created:
            command("docker", "stop", container)


if __name__ == "__main__":
    main()
