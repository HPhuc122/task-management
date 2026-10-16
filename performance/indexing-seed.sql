-- Synthetic fixture for the disposable database created by indexing-benchmark.py.
-- Never run this file against a development/demo database: it replaces seed data.
TRUNCATE users, categories RESTART IDENTITY CASCADE;

INSERT INTO users (email, password_hash, role)
SELECT 'index-user-' || n || '@example.invalid', 'not-a-login-hash', 'USER'
FROM generate_series(1, 1000) AS n;

INSERT INTO tasks (title, description, status, priority, due_date, user_id)
SELECT 'Benchmark task ' || n,
       repeat('Synthetic task description. ', 4),
       CASE n % 3 WHEN 0 THEN 'TODO' WHEN 1 THEN 'IN_PROGRESS' ELSE 'DONE' END,
       CASE n % 3 WHEN 0 THEN 'LOW' WHEN 1 THEN 'MEDIUM' ELSE 'HIGH' END,
       CASE WHEN n % 5 = 0 THEN NULL
            ELSE TIMESTAMPTZ '2026-01-01 00:00:00+00' + (n % 365) * INTERVAL '1 day' END,
       ((n - 1) % 1000) + 1
FROM generate_series(1, 200000) AS n;

-- Keep statistics unchanged across paired measurements; use a warm buffer cache.
ALTER TABLE tasks SET (autovacuum_enabled = false);
VACUUM ANALYZE tasks;
ANALYZE users;
