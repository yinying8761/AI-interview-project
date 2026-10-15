"""Run one synthetic interview through the Java-to-Python-to-model chain."""
from pathlib import Path
import json, os, sys, time
import requests
ROOT=Path(__file__).resolve().parents[1]
BASE=os.getenv('ACCEPTANCE_API','http://127.0.0.1:8088/api')
steps=[]
token=None
def api(method,path,payload=None,timeout=180):
    r=requests.request(method,BASE+path,json=payload,headers={'Authorization':'Bearer '+token} if token else {},timeout=timeout)
    result=r.json()
    steps.append({'method':method,'path':path,'httpStatus':r.status_code,'code':result.get('code'),'message':result.get('message')})
    if result.get('code')!=200: raise RuntimeError(f'{path}: {result.get("code")} {result.get("message")}')
    return result.get('data')
try:
    token=api('POST','/users/login',{'username':'test01','password':'123456'},10)['token']
    scene=api('GET','/scenarios',timeout=10)[0]
    record=api('POST','/interview-sessions',{'scenarioId':scene['id'],'metadataJson':'{"type":"llm","source":"automated-acceptance"}'})
    rid=record['id']
    state=api('GET',f'/interview-sessions/{rid}/conversation')
    if not state.get('transcript'): state=api('POST',f'/interview-sessions/{rid}/conversation/opening',{})['session']
    assert state['transcript'] and state['transcript'][0]['role']=='assistant'
    question=state['transcript'][0]['content']
    response=api('POST',f'/interview-sessions/{rid}/conversation',{
        'message':'我会先确认需求和接口契约，再用单元测试检查正常、异常与越权流程，最后根据真实测试结果修复问题。',
        'submitId':'automated-acceptance-'+str(rid)})
    assert len([m for m in response['session']['transcript'] if m['role']=='user'])==1
    final=api('POST',f'/interview-sessions/{rid}/complete',{})
    found=next(r for r in api('GET','/records/my',timeout=10) if r['id']==rid)
    report=json.loads(found['reportJson'])
    assert report['recordId']==rid and report['input'] and report['questionEvaluation']
    assert report['textStatus']=='已评估' and report['visionStatus']=='未评估'
    assert report['finalScore'] is None
    steps.append({'check':'追溯报告','passed':True,'recordId':rid,'textStatus':report['textStatus'],'visionStatus':report['visionStatus']})
    # Keep the synthetic question and evaluation summary as evidence. Exclude tokens and raw model internals.
    outcome={'passed':True,'recordId':rid,'question':question,'questionScore':report['questionScore'],'finalScore':report['finalScore'],'steps':steps}
except Exception as exc:
    outcome={'passed':False,'error':str(exc),'steps':steps}
finally:
    (ROOT/'_build/ai-acceptance.json').write_text(json.dumps(outcome,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(outcome,ensure_ascii=False))
if not outcome['passed']: sys.exit(1)
