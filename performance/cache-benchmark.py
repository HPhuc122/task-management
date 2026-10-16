import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'target/cache-benchmark'
OUT.mkdir(parents=True, exist_ok=True)
PROJECT = 'taskflow-cache-benchmark'
MAVEN = 'maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320'
JAVA = 'eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5'
K6 = 'grafana/k6@sha256:3cd0ae83b47a29b273193eb998740c3b0e219fc34cdf579b90a8fc1698f5c025'
COMPOSE = ['docker','compose','-p',PROJECT,'-f',str(ROOT/'docker-compose.clean-test.yml')]
APP_NAMES = {mode: PROJECT+'-'+mode for mode in ('on','off')}

def cmd(args, input_text=None, log=None):
    p = subprocess.run(args, input=input_text, text=True, encoding='utf-8',
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=ROOT)
    if log:
        Path(log).write_text(p.stdout+'\n'+p.stderr, encoding='utf-8')
    if p.returncode:
        # Do not echo command arguments: environment arguments may contain secrets.
        raise RuntimeError('Command failed; see diagnostic log' if log else p.stderr[-1800:])
    return p.stdout.strip()

def db(query):
    return cmd(COMPOSE+['exec','-T','postgres','sh','-c',
               'exec psql -X -q -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], query)

def redis(*args):
    return cmd(COMPOSE+['exec','-T','redis','redis-cli',*args])

def info():
    return dict(line.split(':',1) for line in redis('INFO','stats').splitlines()
                if ':' in line and not line.startswith('#'))

def wait_http(port):
    for attempt in range(120):
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/v3/api-docs',timeout=3) as r:
                if r.status == 200:
                    return
        except Exception:
            time.sleep(1)
    raise RuntimeError('Application did not become ready')

def run_load(mode, duration, name):
    path = OUT/(name+'.json')
    env = os.environ.copy()
    env['ADMIN_PASSWORD'] = app_environment['DEMO_ADMIN_PASSWORD']
    # Only pass the password through the environment; never write it to disk.
    args=['docker','run','--rm','--network',PROJECT+'_default',
          '--mount',f'type=bind,source={OUT},target=/results',
          '-e','ADMIN_PASSWORD','-e','BASE_URL=http://'+APP_NAMES[mode]+':8080',
          '-e','DURATION='+duration,'-e','RESULT_FILE=/results/'+path.name,
          K6,'run','--quiet','/results/load.js']
    p=subprocess.run(args, env=env, text=True, encoding='utf-8', stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    (OUT/(name+'.console.log')).write_text(p.stdout+'\n'+p.stderr,encoding='utf-8')
    if p.returncode:
        raise RuntimeError('k6 failed; see its console log')
    result=json.loads(path.read_text())
    if result['metrics']['category_error_rate']['values']['rate'] != 0:
        raise RuntimeError('Category request failed')
    return result

source = (ROOT/'performance/category-benchmark.js').read_text(encoding='utf-8-sig')
source = source.replace('vus: 1,','vus: 5,').replace("duration: '10s',","duration: __ENV.DURATION,")
source += '''\n// Export metrics only: setup_data contains a JWT and is deliberately omitted.\nexport function handleSummary(data) {\n  return { [__ENV.RESULT_FILE]: JSON.stringify({metrics: data.metrics}, null, 2) };\n}\n'''
(OUT/'load.js').write_text(source,encoding='utf-8')
override=OUT/'postgres-statements.json'
override.write_text(json.dumps({'services':{'postgres':{'command':['postgres','-c','shared_preload_libraries=pg_stat_statements']}}}),encoding='utf-8')
COMPOSE += ['-f',str(override)]
started=[]
try:
    for mode in ('on','off'):
        build=OUT/('build-'+mode)
        build.mkdir(exist_ok=True)
        shutil.copy2(ROOT/'pom.xml',build/'pom.xml')
        shutil.copytree(ROOT/'src',build/'src',dirs_exist_ok=True)
        if mode=='off':
            config=build/'src/main/java/com/taskmanagement/config/RedisCacheConfig.java'
            text=config.read_text(encoding='utf-8')
            if text.count('\n@EnableCaching\n') != 1:
                raise RuntimeError('Expected exactly one cache activation annotation')
            config.write_text(text.replace('\n@EnableCaching\n','\n'),encoding='utf-8')
        print('Building temporary cache-'+mode+' variant',flush=True)
        cmd(['docker','run','--rm','--mount',f'type=bind,source={build},target=/workspace',
             '--mount','type=volume,source=task_management_maven_cache,target=/root/.m2',
             '-w','/workspace',MAVEN,'mvn','-B','-o','-DskipTests','package'], log=OUT/('build-'+mode+'.log'))
    print('Starting isolated dependencies',flush=True)
    cmd(COMPOSE+['up','-d','postgres','redis','minio','minio-ready'],log=OUT/'compose-start.log')
    for attempt in range(60):
        try:
            db('CREATE EXTENSION IF NOT EXISTS pg_stat_statements;')
            break
        except RuntimeError:
            if attempt==59: raise
            time.sleep(1)
    # Resolve Compose configuration in memory; it contains secrets and is never exported.
    cfg=json.loads(cmd(COMPOSE+['config','--format','json']))
    app_environment=cfg['services']['app']['environment'].copy()
    app_environment.update({
        'LOGGING_LEVEL_ORG_HIBERNATE_SQL':'OFF',
        'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND':'OFF',
        'LOGGING_LEVEL_COM_TASKMANAGEMENT':'INFO',
        'SPRING_JPA_SHOW_SQL':'false',
    })
    records=[]
    for mode,port in (('on',18081),('off',18082)):
        args=['docker','run','-d','--rm','--name',APP_NAMES[mode],'--network',PROJECT+'_default',
              '--cpus','1','--memory','512m','-p',f'127.0.0.1:{port}:8080',
              '--mount','type=bind,source='+str(OUT/('build-'+mode)/'target/task-management-0.0.1-SNAPSHOT.jar')+',target=/app/app.jar,readonly']
        for key,value in app_environment.items(): args+=['-e',key+'='+str(value)]
        args += [JAVA,'java','-Xms128m','-Xmx256m','-jar','/app/app.jar']
        cmd(args)
        started.append(APP_NAMES[mode])
        wait_http(port)
        print('Initial 60s warm-up: '+mode,flush=True)
        run_load(mode,'60s','initial-warmup-'+mode)
    print('Both variants ready; starting 3 paired rounds',flush=True)
    for round_no in range(1,4):
        for mode in (('off','on') if round_no%2 else ('on','off')):
            name=f'round-{round_no}-{mode}'
            redis('DEL','boot4::categories::all')
            run_load(mode,'20s',name+'-warmup')
            cache_exists=int(redis('EXISTS','boot4::categories::all'))
            if cache_exists != (1 if mode=='on' else 0):
                raise RuntimeError('Cache activation state does not match experiment condition')
            ttl=int(redis('TTL','boot4::categories::all'))
            db('SELECT pg_stat_statements_reset();')
            before=info()
            result=run_load(mode,'30s',name)
            after=info()
            counts=db("SELECT json_build_object('calls',coalesce(sum(calls),0),'rows',coalesce(sum(rows),0)) "
                      "FROM pg_stat_statements WHERE query ~* 'from[[:space:]]+(public[.])?categories([[:space:]]|$)';")
            record={'round':round_no,'mode':mode,'metrics':result['metrics'],
                    'category_sql':json.loads(counts),'redis_key_exists_before_measurement':cache_exists,
                    'redis_ttl_before_measurement':ttl,
                    'redis_hits':int(after['keyspace_hits'])-int(before['keyspace_hits']),
                    'redis_misses':int(after['keyspace_misses'])-int(before['keyspace_misses'])}
            records.append(record)
            (OUT/'measurements.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
            m=result['metrics']
            print(name+': '+json.dumps({'requests':m['category_requests']['values']['count'],
                  'avg_ms':m['category_latency_ms']['values']['avg'],
                  'p95_ms':m['category_latency_ms']['values']['p(95)'],
                  'rate':m['category_requests']['values']['rate'],'category_sql':record['category_sql'],
                  'redis_hits':record['redis_hits']}),flush=True)
    metadata={'recorded_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'source_commit':cmd(['git','rev-parse','HEAD']),'local_changes':True,
              'spring_boot':'4.0.8','java_image':JAVA,'maven_image':MAVEN,'k6_image':K6,
              'app_resources':'1 CPU, 512 MiB memory, heap 128-256 MiB per variant',
              'postgres':db('SELECT version();'),'category_count':int(db('SELECT count(*) FROM categories;')),
              'flyway':db('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;'),
              'docker_engine':cmd(['docker','version','--format','{{.Server.Version}}']),
              'docker_host':cmd(['docker','info','--format','{{.OSType}}/{{.Architecture}} CPUs={{.NCPU}} RAM={{.MemTotal}}']),
              'redis_image':cfg['services']['redis']['image'],'postgres_image':cfg['services']['postgres']['image'],
              'warmup':'60 seconds per variant initially; 20 seconds before each measurement',
              'measurement':'5 VUs, 30 seconds, 3 paired rounds; OFF/ON, ON/OFF, OFF/ON',
              'difference':'Only the @EnableCaching annotation is removed in the temporary OFF source copy',
              'jars_sha256':{mode:hashlib.sha256((OUT/('build-'+mode)/'target/task-management-0.0.1-SNAPSHOT.jar').read_bytes()).hexdigest() for mode in ('on','off')}}
    (OUT/'metadata.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    print('COMPLETE; sanitized results in target/cache-benchmark',flush=True)
finally:
    for name in started:
        cmd(['docker','stop',name])
    cmd(COMPOSE+['stop'],log=OUT/'compose-stop.log')
