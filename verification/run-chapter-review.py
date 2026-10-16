import argparse,base64,json,secrets,subprocess
parser=argparse.ArgumentParser()
parser.add_argument("--offline",action="store_true")
options=parser.parse_args()
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'target/chapter-review'
OUT.mkdir(parents=True,exist_ok=True)
PROJECT='taskflow-chapter-review'
MAVEN='maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320'
override=OUT/'test-services.json'
override.write_text(json.dumps({'services':{'minio':{'environment':{
 'MINIO_ROOT_USER':'test-access-key','MINIO_ROOT_PASSWORD':'test-secret-key'}}}}),encoding='utf-8')
compose=['docker','compose','-p',PROJECT,'-f',str(ROOT/'docker-compose.clean-test.yml'),'-f',str(override)]
def call(args,log=None):
 p=subprocess.run(args,cwd=ROOT,text=True,encoding='utf-8',stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if log: Path(log).write_text(p.stdout+'\n'+p.stderr,encoding='utf-8')
 if p.returncode: raise RuntimeError('Command failed; see local diagnostic log')
 return p.stdout.strip()
call(compose+['up','-d','postgres','redis','rabbitmq','mailpit','minio','minio-ready'],OUT/'services-start.log')
cfg=json.loads(call(compose+['config','--format','json']))
app=cfg['services']['app']['environment']
env={k:str(v) for k,v in app.items() if k not in ('SPRING_PROFILES_ACTIVE','S3_BUCKET')}
env.update({'RUN_DATABASE_TESTS':'true','RUN_ATTACHMENT_TESTS':'true','RUN_NOTIFICATION_TESTS':'true',
 'JWT_SECRET':base64.b64encode(secrets.token_bytes(32)).decode(),
 'MAILPIT_API':'http://mailpit:8025','SPRING_PROFILES_ACTIVE':'dev',
 'MAVEN_OPTS':'-Xmx384m','NOTIFICATIONS_ENABLED':'false'})
args=['docker','run','--rm','--name',PROJECT+'-verify','--network',PROJECT+'_default',
 '--mount',f'type=bind,source={ROOT},target=/workspace',
 '--mount','type=volume,source=task_management_maven_cache,target=/root/.m2']
for k,v in env.items(): args+=['-e',k+'='+v]
script='''mkdir -p /build; cp /workspace/pom.xml /build/; cp -r /workspace/src /build/; cd /build
mvn -B -o -DargLine=-Xmx512m verify > /workspace/target/chapter-review/verify.log 2>&1
result=$?
cp -r target/surefire-reports /workspace/target/chapter-review/
if [ -f target/task-management-0.0.1-SNAPSHOT.jar ]; then cp target/task-management-0.0.1-SNAPSHOT.jar /workspace/target/chapter-review/; fi
exit $result'''
if not options.offline:
 script=script.replace('mvn -B -o ', 'mvn -B ')
args += [MAVEN,'sh','-c',script]
call(args,OUT/'verify-console.log')
print('Full mvn verify completed successfully. Dependencies remain running for HTTP/AOP evidence.',flush=True)
