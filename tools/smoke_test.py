"""Exercise the running Java API using synthetic users; never save tokens."""
import json, os, sys, uuid
from datetime import datetime, timedelta
from pathlib import Path
import requests

ROOT=Path(__file__).resolve().parents[1]
BASE=os.getenv('ACCEPTANCE_API','http://127.0.0.1:8088/api')
rows=[]
def call(method,path,body=None,token=None):
    r=requests.request(method,BASE+path,json=body,headers={'Authorization':'Bearer '+token} if token else {},timeout=150)
    try: payload=r.json()
    except ValueError: payload={'message':r.text[:200]}
    return r.status_code,payload
def check(name,method,path,body=None,token=None,expected=200,assertion=None):
    http,p=call(method,path,body,token)
    ok=(p.get('code',http)==expected) and (assertion(p.get('data')) if assertion else True)
    safe=json.loads(json.dumps(p))
    if isinstance(safe.get('data'),dict):
        for key in ('token','accessToken','refreshToken'): safe['data'].pop(key,None)
    rows.append({'name':name,'method':method,'path':path,'http':http,'expectedCode':expected,'actualCode':p.get('code',http),'passed':bool(ok),'response':safe})
    print(('PASS ' if ok else 'FAIL ')+name,flush=True)
    return p.get('data')
def main():
    check('未登录访问记录','GET','/records',expected=401)
    tokens={}
    for name,pw in [('test01','123456'),('admin','admin123'),('host01','123456')]:
        data=check('登录 '+name,'POST','/users/login',{'username':name,'password':pw})
        if not data or not data.get('token'): raise RuntimeError('登录失败：'+name)
        tokens[name]=data['token']
    check('空密码参数校验','POST','/users/login',{'username':'test01','password':''},expected=400)
    scenarios=check('读取真实场景','GET','/scenarios',token=tokens['test01'],assertion=lambda d:bool(d))
    sid=scenarios[0]['id']
    start=datetime.now()+timedelta(days=1,minutes=int(uuid.uuid4().int%50000))
    body={'scenarioId':sid,'scheduledAt':start.isoformat(timespec='seconds'),'plannedEndAt':(start+timedelta(minutes=30)).isoformat(timespec='seconds'),'metadataJson':'{"type":"llm","source":"acceptance"}'}
    record=check('候选人预约落库','POST','/interview-sessions',body,tokens['test01'],assertion=lambda d:d and d['status']=='pending')
    rid=record['id']
    for user in tokens:
        check('刷新及换角色查询 '+user,'GET','/interview-sessions',token=tokens[user],assertion=lambda d:any(r['id']==rid for r in d))
    check('重复时间预约冲突','POST','/interview-sessions',body,tokens['test01'],expected=400)
    check('不存在场景','POST','/interview-sessions',{'scenarioId':999999},tokens['test01'],expected=400)
    check('管理员不能冒充候选人预约','POST','/interview-sessions',body,tokens['admin'],expected=403)
    user='qa'+uuid.uuid4().hex[:10]
    check('注册第二位模拟候选人','POST','/users/register',{'username':user,'password':'Demo123456','email':user+'@example.com'})
    _,other=call('POST','/users/login',{'username':user,'password':'Demo123456'})
    other_token=other['data']['token']
    check('他人记录详情权限','GET',f'/interview-sessions/{rid}',token=other_token,expected=403)
    check('他人状态快照权限','GET',f'/interview-sessions/{rid}/state',token=other_token,expected=403)
    check('取消预约','POST',f'/interview-sessions/{rid}/cancel',{},tokens['test01'])
    check('已取消记录不能启动','POST',f'/interview-sessions/{rid}/start',{},tokens['test01'],expected=400)
    for name,token in tokens.items(): check('退出 '+name,'POST','/users/logout',token=token)
    check('退出后令牌失效','GET','/records',token=tokens['test01'],expected=401)
try:
    main()
finally:
    out=ROOT/'_build/api-results.json'
    out.write_text(json.dumps({'baseUrl':BASE,'executedAt':datetime.now().isoformat(),'results':rows},ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'{sum(r["passed"] for r in rows)}/{len(rows)} passed')
if not all(r['passed'] for r in rows): sys.exit(1)
