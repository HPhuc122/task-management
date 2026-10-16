import json
from pathlib import Path
import statistics

root=Path(__file__).resolve().parents[1]
out=root/'target/cache-benchmark'
records=json.loads((out/'measurements.json').read_text())
assert len(records)==6
rows=[]
for r in records:
    m=r['metrics']
    v=lambda name: m[name]['values']
    n=v('category_requests')['count']
    assert v('category_error_rate')['rate']==0
    assert r['category_sql']['calls']==(n if r['mode']=='off' else 0)
    assert r['redis_hits']==(n if r['mode']=='on' else 0)
    assert r['redis_misses']==0
    rows.append({'round':r['round'],'mode':r['mode'],'requests':n,
                 'avg_ms':v('category_latency_ms')['avg'],
                 'p95_ms':v('category_latency_ms')['p(95)'],
                 'requests_per_second':v('category_requests')['rate'],
                 'error_rate':v('category_error_rate')['rate'],
                 'category_sql_calls':r['category_sql']['calls'],
                 'redis_hits':r['redis_hits'],'redis_misses':r['redis_misses']})
summary={}
for mode in ('off','on'):
    selected=[r for r in rows if r['mode']==mode]
    summary[mode]={key:statistics.mean(r[key] for r in selected)
                   for key in ('avg_ms','p95_ms','requests_per_second','error_rate')}
    summary[mode]['total_requests']=sum(r['requests'] for r in selected)
    summary[mode]['category_sql_calls']=sum(r['category_sql_calls'] for r in selected)
    summary[mode]['redis_hits']=sum(r['redis_hits'] for r in selected)
summary['change_percent']={
    'avg_latency_reduction':100*(1-summary['on']['avg_ms']/summary['off']['avg_ms']),
    'p95_reduction':100*(1-summary['on']['p95_ms']/summary['off']['p95_ms']),
    'throughput_increase':100*(summary['on']['requests_per_second']/summary['off']['requests_per_second']-1)}
(out/'summary.json').write_text(json.dumps({'runs':rows,'summary':summary},indent=2),encoding='utf-8')
text=['# Redis Cache benchmark — 2026-10-16','','Spring Boot 4.0.8; 3 categories; 5 VUs; 30 seconds/run; three paired rounds.',
      'Initial warm-up: 60 seconds/variant; before every measured run: 20 seconds.',
      'Order: OFF/ON, ON/OFF, OFF/ON. Same source except @EnableCaching in the temporary OFF copy.',
      'Each variant: 1 CPU, 512 MiB RAM, Java heap 128–256 MiB. Same PostgreSQL and Redis services.',
      '', '| Round | Cache | Requests | Average ms | P95 ms | Requests/s | Error rate | Category SQL calls | Redis hits |',
      '|---|---|---|---|---|---|---|---|---|']
for r in rows:
    text.append(f"| {r['round']} | {r['mode']} | {r['requests']} | {r['avg_ms']:.3f} | {r['p95_ms']:.3f} | {r['requests_per_second']:.3f} | 0% | {r['category_sql_calls']} | {r['redis_hits']} |")
text += ['', 'Arithmetic mean of the three run averages/P95s/rates per condition; P95 is not pooled.',
         '',json.dumps(summary,indent=2),'',
         'Category SQL calls are measured by pg_stat_statements. Authentication SQL is not included in that count.',
         'Redis counters were sampled only during measured load, after cache warm-up: zero misses in all measured ON runs.',
         'A zero Category SQL count does not mean zero database queries for the HTTP request.',
         'Synthetic local closed-loop workload on three rows; limited runs, shared host/background load, CPU quotas, no production capacity claim.',
         'Only metrics are exported from k6; setup_data and JWTs are excluded.']
(out/'summary.md').write_text('\n'.join(text)+'\n',encoding='utf-8')
print('\n'.join(text))
