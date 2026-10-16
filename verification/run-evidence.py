import base64,json,secrets,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'target/chapter-review'
compose=['docker','compose','-p','taskflow-chapter-review','-f',str(ROOT/'docker-compose.clean-test.yml'),'-f',str(OUT/'test-services.json')]
cfg=json.loads(subprocess.check_output(compose+['config','--format','json'],cwd=ROOT,text=True,encoding='utf-8'))
env=cfg['services']['app']['environment'].copy()
env.update({'SPRING_PROFILES_ACTIVE':'dev,demo','DEMO_USER_PASSWORD':'local-demo-user-pass',
 'DEMO_ADMIN_PASSWORD':'local-demo-admin-pass','JWT_SECRET':base64.b64encode(secrets.token_bytes(32)).decode(),
 'S3_ACCESS_KEY':'test-access-key','S3_SECRET_KEY':'test-secret-key','S3_BUCKET':'chapter-evidence',
 'SPRING_JPA_SHOW_SQL':'false','LOGGING_LEVEL_ORG_HIBERNATE_SQL':'OFF'})
args=['docker','run','--rm','--name','taskflow-chapter-evidence','--network','taskflow-chapter-review_default',
 '--mount',f'type=bind,source={ROOT},target=/workspace']
for k,v in env.items(): args+=['-e',str(k)+'='+str(v)]
script='''mkdir -p /probe; cd /probe
jar xf /workspace/target/chapter-review/task-management-0.0.1-SNAPSHOT.jar
javac -cp 'BOOT-INF/classes:BOOT-INF/lib/*' -d /probe /workspace/verification/ChapterEvidence.java
java -Xmx512m -cp '/probe:BOOT-INF/classes:BOOT-INF/lib/*' com.taskmanagement.controller.ChapterEvidence'''
args+=['maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320','sh','-c',script]
with (OUT/'aop-runtime.log').open('w',encoding='utf-8') as log:
 p=subprocess.run(args,stdout=log,stderr=subprocess.STDOUT)
raise SystemExit(p.returncode)
