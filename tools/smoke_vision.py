"""Exercise a fresh synthetic face through vision capture and the fused Java report."""
from pathlib import Path
from io import BytesIO
import json
import os
import sys
import time

from PIL import Image
import requests

ROOT = Path(__file__).resolve().parents[1]
JAVA = os.getenv('ACCEPTANCE_API', 'http://127.0.0.1:8088/api')
AI = os.getenv('ACCEPTANCE_AI', 'http://127.0.0.1:8765/api/ai')
steps = []
token = None


def java(method, path, payload=None, timeout=180):
    response = requests.request(method, JAVA + path, json=payload,
                                headers={'Authorization': 'Bearer ' + token} if token else {}, timeout=timeout)
    value = response.json()
    steps.append({'path': path, 'httpStatus': response.status_code, 'code': value.get('code')})
    if value.get('code') != 200:
        raise RuntimeError(f'{path}: {value.get("code")} {value.get("message")}')
    return value.get('data')


try:
    token = java('POST', '/users/login', {'username': 'test01', 'password': '123456'}, 10)['token']
    scene = java('GET', '/scenarios', timeout=10)[0]
    record = java('POST', '/interview-sessions', {'scenarioId': scene['id'],
                  'metadataJson': '{"type":"llm","source":"synthetic-vision-acceptance"}'})
    rid = record['id']
    state = java('GET', f'/interview-sessions/{rid}/conversation')
    session_id = state['id']
    if not state.get('transcript'):
        state = java('POST', f'/interview-sessions/{rid}/conversation/opening', {})['session']
    assert state['transcript'][0]['role'] == 'assistant'

    with Image.open(ROOT / 'data/测试数据/合成面试人像.png') as image:
        image = image.convert('RGB')
        image.thumbnail((640, 480))
        output = BytesIO()
        image.save(output, format='JPEG', quality=85)
        frame = output.getvalue()
    for index in range(50):
        response = requests.post(f'{AI}/sessions/{session_id}/frames',
                                 files={'image': ('synthetic-face.jpg', frame, 'image/jpeg')}, timeout=15)
        if response.status_code != 200:
            raise RuntimeError(f'vision frame {index}: HTTP {response.status_code}: {response.text[:200]}')
        time.sleep(.25)
    metrics = requests.get(f'{AI}/sessions/{session_id}/metrics', timeout=10).json()
    steps.append({'check': 'fresh-synthetic-frame', 'processedFrames': metrics.get('metrics', {}).get('processed_frames'),
                  'faceDetected': metrics.get('metrics', {}).get('face_detected')})

    java('POST', f'/interview-sessions/{rid}/conversation', {
        'message': '我会用线程池执行独立任务，通过 CompletableFuture 汇总结果，限定并发和超时时间，再比较吞吐量与错误率。',
        'submitId': f'synthetic-vision-{rid}'})
    java('POST', f'/interview-sessions/{rid}/complete', {})
    found = next(value for value in java('GET', '/records/my', timeout=10) if value['id'] == rid)
    report = json.loads(found['reportJson'])
    assert report['textStatus'] == '已评估', report
    assert report['visionStatus'] == '已评估', report
    assert isinstance(report['finalScore'], (float, int)), report
    assert report['input'] and report['videoSummary']['face_frames'] >= 30, report
    vision_score = round((report['videoSummary']['expression_score'] +
                          report['videoSummary']['attention_score']) / 2, 1)
    outcome = {'passed': True, 'recordId': rid, 'textScore': report['questionScore'],
               'visionScore': vision_score, 'finalScore': report['finalScore'],
               'faceFrames': report['videoSummary']['face_frames'], 'steps': steps}
except Exception as exc:
    outcome = {'passed': False, 'error': str(exc)[:1000], 'steps': steps}
finally:
    (ROOT / '_build/vision-acceptance.json').write_text(json.dumps(outcome, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(outcome, ensure_ascii=False))
if not outcome['passed']:
    sys.exit(1)
