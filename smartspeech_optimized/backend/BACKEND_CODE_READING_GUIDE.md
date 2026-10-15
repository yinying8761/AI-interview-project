# 后端代码阅读指南：从 `run.py` 看到每一条业务线

这份文档专门回答一个问题：

> 我只知道以前从 `app.py` 开始看，现在这个项目没有 `app.py`，请求到底是怎么一步步跑到其他 Python 文件里的？

你可以先把整个后端想成一家餐厅：

- `run.py`：餐厅大门。它负责把餐厅开起来。
- `app/__init__.py`：总经理。它负责招聘员工、布置部门、挂好各个窗口的牌子。
- `app/api/`：前台窗口。它接收顾客请求，检查请求是否合法。
- `app/services/`：后厨。真正的出题、识别语音、分析人脸、调用大模型都在这里。
- `app/storage/`：仓库。它负责保存会话和报告。
- `data/` 和 `runtime/`：实际存放文件和运行数据的地方。

一句话理解调用关系：

```text
浏览器
  -> run.py
  -> app/__init__.py 的 create_app()
  -> app/api/ 里的某个蓝图路由
  -> app/services/ 里的某个 Service
  -> data/、runtime/ 或外部模型/API
  -> 原路返回 JSON、音频或错误
```

## 1. 先纠正一个最容易误会的地方

这个项目**没有 `backend/app.py`**。

以前常见的写法是：

```text
app.py
  -> 直接写 @app.route(...)
  -> 同一个文件里继续写业务逻辑
```

本项目的写法是：

```text
run.py
  -> create_app() 创建 Flask 应用
  -> 注册蓝图
  -> 蓝图里的视图函数调用 Service
  -> Service 再访问文件、模型或外部 API
```

它们不是两套完全不同的东西。无论哪种写法，最终都是在处理同一个 HTTP 请求，只是本项目的代码被拆成了更多小文件。

## 2. 后端目录地图

```text
backend/
|-- run.py                         真正的启动入口
|-- app/
|   |-- __init__.py                创建 Flask 应用、注册总蓝图
|   |-- config.py                  路径、端口、模型、密钥等配置
|   |-- api/
|   |   |-- __init__.py            把几个子蓝图组装成一个总蓝图
|   |   |-- common.py              登录检查、读取 JSON、取得 Service 等公共工具
|   |   |-- system.py              健康检查
|   |   |-- auth.py                注册、登录、人脸登录
|   |   |-- resume.py              简历上传、查询、删除
|   |   |-- interview.py           面试创建、答题、摄像头帧、结束和报告
|   |   `-- media.py               语音转文字、文字转语音
|   |-- services/
|   |   |-- container.py           所有 Service 的“工具柜”
|   |   |-- auth_service.py        账号保存和密码校验
|   |   |-- face_recognition.py    人脸特征提取与匹配
|   |   |-- resume_service.py      简历文字提取
|   |   |-- deepseek_service.py    调用 DeepSeek API
|   |   |-- interview_service.py   面试提问和综合评价的提示词
|   |   |-- question_bank.py       本地题库
|   |   |-- asr_service.py         Whisper 语音识别
|   |   |-- tts_service.py         语音合成
|   |   |-- score_service.py       问答、表情、注意力分数融合
|   |   `-- vision/                摄像头画面分析
|   `-- storage/
|       |-- session_store.py       正在进行的面试会话
|       `-- report_store.py        已完成的面试报告
`-- tests/                         后端测试，也能帮助你看懂调用方式
```

## 3. 程序启动时到底发生了什么

项目通过下面的命令启动：

```powershell
python -m backend.run
```

执行顺序可以写成下面这条链：

```text
python -m backend.run
  -> 执行 backend/run.py
  -> from backend.app import create_app
  -> app = create_app()
  -> create_app() 创建并配置 Flask
  -> 创建 ServiceContainer
  -> 注册 /api 蓝图
  -> serve(...) 启动 Waitress
  -> 浏览器访问 http://127.0.0.1:8765
```

### 3.1 `backend/run.py`：只管启动服务器

`run.py` 属于“门口保安”，它主要做四件事：

1. 导入 `create_app`。
2. 调用 `app = create_app()`。
3. 如果是直接运行本文件，就打印启动信息。
4. 调用 Waitress 的 `serve()` 监听端口。

它没有写业务路由，也没有写登录逻辑。不要在 `run.py` 里找 `/api/auth/login`，那里找不到。

### 3.2 `backend/app/__init__.py`：真正组装应用

`create_app()` 是整个后端的装配车间。它按顺序做了这些事：

1. 创建 Flask 对象。
2. 从 `Config` 读取配置。
3. 开启跨域访问，允许 Vue 开发服务器调用 `/api/*`。
4. 创建 `ServiceContainer(app)`。
5. 把工具柜放进 `app.extensions["services"]`。
6. 注册 `api` 总蓝图，并统一加上 `/api` 前缀。
7. 注册 413 和 500 等错误处理。
8. 注册 `GET /` 和前端单页应用的回退路由。

需要特别注意最后一步：这个后端不仅提供 API，还直接提供已经构建好的 Vue 页面。

```text
GET /                 -> 返回 frontend/dist/index.html
GET /reports          -> Vue 路由对应的前端页面
GET /api/reports      -> Flask API，返回报告 JSON
```

`/reports` 和 `/api/reports` 看起来相近，但一个返回网页，一个返回数据。

## 4. 什么是蓝图

你可以把蓝图理解成一本“窗口登记册”。

以前的写法像这样：

```python
@app.route("/login")
def login():
    ...
```

蓝图写法是：

```python
# app/api/auth.py
auth_bp = Blueprint("auth", __name__)

@auth_bp.post("/login")
def password_login():
    ...
```

然后在总装配处登记：

```python
app.register_blueprint(api, url_prefix="/api")
```

地址是由两段拼起来的：

```text
Blueprint 自己的 /auth/login
             +
总蓝图注册时的 /api
             =
最终地址 /api/auth/login
```

### 4.1 两级蓝图关系

```text
Flask app
  `-- api 总蓝图（统一前缀 /api）
      |-- system_bp
      |-- auth_bp
      |-- interview_bp
      |-- media_bp
      `-- resume_bp
```

对应代码位置：

- 总蓝图：`backend/app/api/__init__.py`
- 子蓝图：`backend/app/api/auth.py`、`interview.py` 等
- 加 `/api` 前缀：`backend/app/__init__.py`

所以 `/api/auth/login` 最终会跑到 `auth.py` 里的 `password_login()`。

### 4.2 `services()` 是什么

蓝图里经常出现这一句：

```python
user = services().auth.authenticate_password(username, password)
```

它不是再从某个文件临时创建对象，而是从 Flask 应用的工具柜里取出已经准备好的 Service：

```text
services()
  -> current_app.extensions["services"]
  -> app/__init__.py 中创建的 ServiceContainer
```

它的定义在 `backend/app/api/common.py`。

可以这样理解：

- `services()`：打开工具柜。
- `.auth`：拿出账号工具。
- `.authenticate_password(...)`：使用账号工具检查用户名和密码。

## 5. 完整接口总表

下面所有路径都应加 `/api`。例如表里的 `/health`，实际地址是 `/api/health`。

| 方法 | 完整路径 | 路由文件 | 主要作用 |
| --- | --- | --- | --- |
| `GET` | `/api/health` | `api/system.py` | 查看服务状态和能力 |
| `POST` | `/api/auth/register` | `api/auth.py` | 注册账号，可同时录入人脸 |
| `POST` | `/api/auth/login` | `api/auth.py` | 用户名密码登录 |
| `POST` | `/api/auth/face-login` | `api/auth.py` | 人脸登录 |
| `POST` | `/api/auth/face-register` | `api/auth.py` | 给已登录账号绑定人脸 |
| `POST` | `/api/auth/logout` | `api/auth.py` | 退出登录 |
| `GET` | `/api/auth/me` | `api/auth.py` | 获取当前登录用户 |
| `POST` | `/api/resumes` | `api/resume.py` | 上传并解析简历 |
| `GET` | `/api/resumes/latest` | `api/resume.py` | 获取最近一次简历摘要 |
| `DELETE` | `/api/resumes/latest` | `api/resume.py` | 删除最近一次简历 |
| `POST` | `/api/interviews` | `api/interview.py` | 创建面试会话 |
| `POST` | `/api/interviews/<id>/opening` | `api/interview.py` | 生成开场和第一题 |
| `GET` | `/api/interviews/<id>` | `api/interview.py` | 获取面试当前状态 |
| `GET` | `/api/interviews/<id>/metrics` | `api/interview.py` | 获取实时表情和注意力指标 |
| `DELETE` | `/api/interviews/<id>` | `api/interview.py` | 放弃本次面试 |
| `POST` | `/api/interviews/<id>/frames` | `api/interview.py` | 提交一帧摄像头画面 |
| `POST` | `/api/interviews/<id>/messages` | `api/interview.py` | 提交回答并生成下一题 |
| `POST` | `/api/interviews/<id>/finish` | `api/interview.py` | 结束面试并生成报告 |
| `GET` | `/api/reports` | `api/interview.py` | 获取当前用户的历史报告列表 |
| `GET` | `/api/reports/<id>` | `api/interview.py` | 获取一份报告详情 |
| `POST` | `/api/media/transcribe` | `api/media.py` | 把录音识别为文字 |
| `POST` | `/api/media/tts` | `api/media.py` | 把文字合成为语音 |
| `GET` | `/api/media/voices` | `api/media.py` | 获取可用音色 |

## 6. 跟着一次登录请求走一遍

这是最重要的阅读方法：不要一次看完全部代码，先顺着一个请求走。

假设用户在登录页输入用户名和密码，前端代码会走到：

```text
frontend/src/pages/LoginPage.vue
  -> frontend/src/stores/auth.js
  -> frontend/src/api/client.js
  -> POST /api/auth/login
```

后端看到请求后的完整路线是：

```text
1. Waitress 收到 HTTP 请求
2. Flask 查找匹配的路由
3. 匹配到 auth_bp 的 POST /auth/login
4. 进入 backend/app/api/auth.py 的 password_login()
5. json_body() 读取 JSON
6. services().auth 取出 AuthService
7. AuthService.authenticate_password() 读取 data/auth/users.json
8. 验证密码哈希
9. begin_user_session() 把 user_id 放进 Flask session
10. 浏览器收到登录结果和签名 Cookie
```

换成代码调用链，就是：

```text
password_login()
  -> common.json_body()
  -> ServiceContainer.auth.authenticate_password()
  -> AuthService 读取 users.json
  -> common.begin_user_session()
  -> jsonify({"user": user})
```

注意两点：

1. 密码不会直接保存。`AuthService` 使用 PBKDF2 生成密码哈希。
2. `begin_user_session()` 不会把密码放进 Cookie，只保存 `user_id`，Cookie 内容由 Flask 签名保护。

之后访问受保护接口时：

```text
@login_required
  -> current_user_id() 从 session 取 user_id
  -> AuthService.get_user(user_id) 确认账号仍存在
  -> 允许进入真正的视图函数
```

## 7. `ServiceContainer`：所有工具的集中存放处

打开 `backend/app/services/container.py`，可以看到应用启动时会准备这些对象：

| 工具柜里的名字 | 实际类 | 谁使用它 | 负责什么 |
| --- | --- | --- | --- |
| `auth` | `AuthService` | `api/auth.py` | 注册、读用户、密码校验、人脸绑定 |
| `face` | `FaceRecognizer` | `api/auth.py` | 提取和匹配人脸特征 |
| `asr` | `ASRService` | `api/media.py` | Whisper 语音转文字 |
| `tts` | `TTSService` | `api/media.py` | 文字转语音 |
| `vision` | `VisionManager` | 摄像头后台线程 | 解码画面并分析表情、注意力 |
| `sessions` | `SessionStore` | `api/interview.py` | 创建、查找、删除进行中的面试 |
| `reports` | `ReportStore` | `api/interview.py` | 保存和读取历史报告 |
| `resumes` | `ResumeService` | `api/resume.py` | 提取和保存简历文字 |
| `question_bank` | `QuestionBankService` | `api/interview.py` | 从本地题库抽题 |

`deepseek` 和 `interview` 是延迟创建的工具：

```python
@property
def deepseek(self):
    if self._deepseek is None:
        self._deepseek = DeepSeekService()
    return self._deepseek

@property
def interview(self):
    if self._interview is None:
        self._interview = InterviewService(self.deepseek)
    return self._interview
```

“延迟创建”就是：现在不用就先不做，第一次真正需要 DeepSeek 时才创建。这样没有配置 API Key 时，应用仍能启动并运行其他功能。

还要区分“创建 Service”和“加载大模型”：

- `ASRService()` 被创建，不代表 Whisper 模型立刻加载；第一次转写时才加载。
- `FaceRecognizer()` 被创建，不代表人脸模型立刻加载；第一次提取人脸时才加载。
- `QuestionBankService()` 会立即读取 `data/interview_question_bank.json`。

## 8. 配置、数据和运行文件放在哪里

所有路径都在 `backend/app/config.py` 中集中定义。

| 内容 | 位置 | 是否长期保存 |
| --- | --- | --- |
| 账号和密码哈希 | `data/auth/users.json` | 是 |
| 人脸特征向量 | `data/auth/faces/*.npy` | 是 |
| 最近一次解析后的简历 | `data/resumes/<哈希目录>/latest.json` | 是 |
| 本地面试题库 | `data/interview_question_bank.json` | 是 |
| Flask 签名密钥 | 环境变量或 `data/.flask_secret` | 是 |
| 正在进行的面试会话 | `SessionStore` 的内存字典 | 否，重启服务会丢失 |
| 已生成的面试报告 | `runtime/reports/<用户>/<会话>.json` | 是 |
| 上传后临时录音 | `runtime/asr` | 识别完成或失败后删除 |
| TTS 音频缓存 | `runtime/tts` | 约保留 24 小时 |
| DOC 临时文件 | `runtime/resumes` | Word 解析后删除 |

几个重要的隐私事实：

- 原始简历文件上传后不会被长期保存，只保存提取出的文字。
- 人脸只保存数值特征向量，不保存原始人脸照片。
- 面试结束只保存指标摘要，不保存原始视频帧或录音。
- 正在面试的对话在内存里，服务重启后无法恢复。

## 9. 简历功能的代码路线

### 上传简历

```text
POST /api/resumes
  -> api/resume.py 的 upload_resume()
  -> request.files.get("resume")
  -> services().resumes.save(...)
  -> ResumeService 根据后缀选择解析方法
       PDF  -> pypdf
       DOCX -> python-docx
       DOC  -> Microsoft Word
  -> 清理并截断文字
  -> 保存到 data/resumes/<哈希目录>/latest.json
  -> 返回简历摘要
```

后端只保留每个用户**最新一份**解析后的简历。再次上传会覆盖上一份。

### 读取和删除简历

```text
GET /api/resumes/latest
  -> ResumeService.latest(user_id)
  -> 返回摘要，不把整篇简历原文发给前端

DELETE /api/resumes/latest
  -> ResumeService.delete(user_id)
  -> 删除 latest.json
```

创建面试时，前端只提交 `resume_id`。后端会再检查这份简历是不是当前登录用户的。这样不能通过伪造 ID 使用别人的简历。

## 10. 面试主线一：创建会话和生成第一题

本项目故意把“创建面试”和“生成第一题”拆成两个请求。原因是第一题可能要调用 DeepSeek，比较慢。先创建页面，再单独等待第一题，用户不会一直停在空白页。

### 第一步：创建面试会话

```text
POST /api/interviews
  -> api/interview.py 的 create_interview()
  -> 检查岗位、难度、音色、追问次数、分析帧率
  -> 检查 resume_id 是否属于当前用户
  -> services().sessions.create(...)
  -> SessionStore 创建一个 InterviewSession
  -> 启动 InterviewVideoAnalyzer
  -> 启动 LatestFrameWorker 后台线程
  -> 返回 201 和 opening_required: true
```

`SessionStore.create()` 在内存字典里保存会话：

```text
self._sessions[会话 ID] = InterviewSession(...)
```

这段代码在 `backend/app/storage/session_store.py`。所以“创建面试”主要不是写数据库，而是创建一个内存对象，并给它启动一个分析画面的后台线程。

### 第二步：生成开场白和第一题

```text
POST /api/interviews/<id>/opening
  -> open_interview()
  -> 检查会话是否属于当前用户
  -> 如果已经存在第一条 assistant 消息，直接返回，避免重复生成
  -> _opening_plan() 决定第一题计划
  -> _generate_question() 生成题目
  -> _apply_plan() 更新面试状态
  -> 把第一题加入 transcript 对话记录
```

第一题有两种情况：

| 是否有简历 | 第一题方式 |
| --- | --- |
| 有简历 | 用简历内容作为依据，调用 DeepSeek 生成开场和第一题 |
| 没有简历 | 从第一个基础场景计划开始，用题库锚点调用 DeepSeek 生成开场和第一题 |

如果 DeepSeek 失败，代码会用一个备用开场问题，并把 `warning` 返回给前端，不会直接把整个面试弄丢。

## 11. 面试主线二：回答问题并生成下一题

接口是：

```text
POST /api/interviews/<id>/messages
```

从进入路由到返回结果，顺序如下：

```text
send_interview_message()
  -> 找到当前用户的面试会话
  -> 检查 status 必须是 active
  -> 读取并检查用户回答
  -> 判断是否已经达到 12 题
  -> _next_plan() 安排下一题
  -> _generate_question() 生成下一题
  -> 保存用户回答
  -> 保存面试官下一题
  -> _apply_plan() 更新题号、场景、追问次数
  -> 返回新增消息和最新 session
```

### 11.1 下一题是怎么安排的

最简单可以记成两条规则：

1. 当前场景还没追问够，就继续追问当前话题。
2. 当前场景追问够了，就切换话题；有简历时，简历问题和基础场景交替进行。

有简历时，大致会看到：

```text
开场（简历）
  -> 追问 1 到 3 次
  -> 基础场景题
  -> 追问 1 到 3 次
  -> 简历项目题
  -> 追问 1 到 3 次
  -> 下一个基础场景
  -> ...
```

没有简历时，大致会看到：

```text
开场（第一个基础场景）
  -> 追问 1 到 3 次
  -> 下一个基础场景
  -> 追问 1 到 3 次
  -> 再下一个基础场景
  -> ...
```

追问次数由 `follow_up_mode` 控制：

- `random`：每个场景随机 1 到 3 次。
- `1`：固定追问 1 次。
- `2`：固定追问 2 次。
- `3`：固定追问 3 次。

### 11.2 题目来自题库还是 DeepSeek

并不是所有题都直接问大模型。

| 题型 `mode` | 题目来源 |
| --- | --- |
| `opening` | DeepSeek 生成开场和第一题 |
| `follow_up` | DeepSeek 根据上一轮回答继续追问 |
| `resume` | DeepSeek 根据简历生成新问题 |
| `scene` | 直接使用本地题库中的原题文字 |

场景主问题直接使用题库原文，是为了保证不会因为上一轮回答而发生串题。追问和简历题才交给 DeepSeek 灵活生成。

### 11.3 12 题后为什么没有第 13 题

题目上限来自配置 `INTERVIEW_QUESTION_LIMIT=12`。

当用户回答第 12 题时：

```text
item.question_count >= item.question_limit
  -> 不再生成 assistant_message
  -> status 改为 checkpoint
  -> checkpoint_reason 改为 question_limit
  -> 返回 checkpoint.required = true
```

这里的 `question_count` 表示已经生成过的面试问题数量，不是回答数量。前端收到检查点后，会询问用户“整体评估”还是“继续下一轮”。

### 11.4 为什么代码里到处有 lock

一个面试会话会同时收到：

- 用户答题请求。
- 摄像头帧请求。
- 指标查询请求。
- 结束请求。

`item.lock` 像一把门锁，防止两个请求同时修改对话列表或状态，把数据弄乱。它不是用来慢慢调用 DeepSeek 的，所以代码只在读取和写入内存状态的小范围内加锁。

## 12. 面试主线三：摄像头画面怎样被分析

摄像头有两个接口：

```text
POST /api/interviews/<id>/frames   提交一帧图片
GET  /api/interviews/<id>/metrics  读取最新指标
```

浏览器不是等后端分析完再继续，而是不断提交图片。后端把分析放到单独线程，接口本身可以快速返回。

### 提交一帧的路线

```text
POST /api/interviews/<id>/frames
  -> analyze_interview_frame()
  -> image_bytes_from_request()
  -> item.vision_worker.submit(image_bytes)
  -> 立即返回 accepted、排队状态和最新指标
```

后台线程自己继续走：

```text
LatestFrameWorker
  -> VisionManager.process_frame()
  -> cv2.imdecode() 解码图片
  -> 缩小到最大宽度 640
  -> InterviewVideoAnalyzer.process_frame()
  -> MediaPipe Face Mesh 找脸部关键点
  -> ExpressionAnalyzer 分析表情
  -> AttentionAnalyzer 分析头部姿态和视线
  -> 更新内存中的实时指标
```

对应文件：

- `services/vision/worker.py`：队列和后台线程。
- `services/vision/manager.py`：解码和缩放图片。
- `services/vision/session_analyzer.py`：汇总一次会话的数据。
- `services/vision/expression_analyzer.py`：表情分析。
- `services/vision/attention_analyzer.py`：头部姿态与注意力分析。

### `queue.Queue(maxsize=1)` 的意思

画面分析速度可能跟不上摄像头提交速度。代码没有无限堆积图片，而是让队列最多只放一张待处理图片。

```text
新图片到来
  -> 队列有空位：放入新图片
  -> 队列已满：丢掉旧图片，替换成最新图片
  -> 正在分析的图片不受影响
```

这样用户看到的是“尽量使用最新画面”，而不是分析几秒钟以前的旧画面。

### 实时指标从哪里来

`GET /metrics` 不去重新分析图片，它只是读取：

```text
item.analyzer.get_live_metrics()
item.vision_worker.status()
```

返回内容能帮助前端显示：

- 是否检测到人脸。
- 当前表情和校准进度。
- 头部俯仰、左右转动、倾斜角度。
- 视线是否偏移。
- 已处理帧数、丢弃帧数、有效分析帧率。
- 后台线程是否健康。

## 13. 面试主线四：结束面试并生成报告

接口是：

```text
POST /api/interviews/<id>/finish
```

完整执行顺序：

```text
finish_interview()
  -> 检查会话和登录用户
  -> 如果已经有报告，直接返回，保证重复点击安全
  -> 检查至少回答过一次
  -> 停止摄像头后台线程
  -> analyzer.stop_session() 生成视频指标摘要
  -> status 改为 evaluating
  -> InterviewService.evaluate() 调用 DeepSeek 综合评分
  -> fuse_interview_scores() 融合问答和视频分数
  -> ReportStore.save() 保存报告 JSON
  -> status 改为 finished
  -> 返回 report
```

对应代码：

```text
api/interview.py::finish_interview
  -> services().interview.evaluate
  -> services().interview 内部再调用 deepseek.chat_json
  -> services().reports.save
  -> ReportStore 写入 runtime/reports/<用户 ID>/<会话 ID>.json
```

### 13.1 最终分数怎么算

有足够有效的视频样本时：

```text
视频综合得分 = 表情得分 × 50% + 注意力得分 × 50%
最终得分 = 问答得分 × 80% + 视频综合得分 × 20%
```

如果有效视频样本不足，视频分数和最终分数会是 `null`。代码不会把视频那 20% 强行加给问答分数，避免制造虚假的高分。

### 13.2 报告保存了什么

报告包含：

- 面试岗位、难度、时间和问答记录。
- DeepSeek 给出的总分、分项分、优点、改进建议。
- 表情、注意力、检测帧数等精简视频摘要。
- 分数融合公式和权重。
- 隐私标记：不保存视频帧、不保存人脸照片、不保存录音。

`ReportStore.list()` 只返回报告摘要，`ReportStore.get()` 才读取完整报告。

## 14. 语音识别的代码路线

前端录音完成后调用：

```text
POST /api/media/transcribe
  -> api/media.py 的 transcribe()
  -> 从 request.files.get("audio") 读取录音
  -> 保存为 runtime/asr/interview_<随机值>.<后缀>
  -> services().asr.transcribe(audio_path)
  -> ASRService.warm_up() 第一次使用时加载 Whisper
  -> Whisper 返回 text、language、segments
  -> finally 中删除临时录音
  -> 返回识别文字和时间片段
```

`finally` 的意思是不论识别成功还是失败，都尽量删除临时录音。ASR 的 `_lock` 防止多个请求同时操作同一个 Whisper 模型。

## 15. 文字转语音的代码路线

前端要朗读问题时调用：

```text
POST /api/media/tts
  -> api/media.py 的 synthesize_speech()
  -> 读取 text 和 voice
  -> services().tts.synthesize(text, voice)
  -> 优先使用 Edge-TTS 在线合成 MP3
  -> 在线失败时，在 Windows 上回退到系统 SAPI 生成 WAV
  -> 检查文件是否真的有内容
  -> send_file() 把音频文件返回给浏览器
```

生成文件放在 `runtime/tts`，调用 `_prune_cache()` 时会清理约一天前的缓存。

`GET /api/media/voices` 用于获取音色列表；在线获取失败时，会返回代码中写好的中文音色列表。

## 16. 注册、人脸登录和人脸绑定的支线

### 注册账号

```text
POST /api/auth/register
  -> 读取 JSON 或 multipart 表单
  -> 如果带图片，先提取人脸特征
  -> AuthService.register_user()
  -> 校验用户名、密码、显示名
  -> PBKDF2 生成密码哈希
  -> 写入 data/auth/users.json
  -> 如果带人脸，写入 data/auth/faces/<用户 ID>.npy
  -> begin_user_session() 自动登录
```

### 人脸登录

```text
POST /api/auth/face-login
  -> 解码浏览器提交的人脸图片
  -> FaceRecognizer.extract_face_vector() 提取 512 维特征
  -> AuthService.list_face_gallery() 读取所有已登记人脸向量
  -> FaceRecognizer.match() 找出距离最近的用户
  -> 距离小于阈值才算匹配
  -> 找到用户后写入 session
```

匹配阈值来自 `FACE_MATCH_THRESHOLD`，默认是 `1.0`。没有匹配结果时返回 401，并提示改用密码登录。

### 给已登录账号补录人脸

```text
POST /api/auth/face-register
  -> @login_required 检查登录
  -> 提取人脸向量
  -> AuthService.register_face(user_id, vector)
  -> 更新 users.json 中的人脸记录
  -> 写入或替换对应的 .npy 文件
```

## 17. 面试状态机

会话状态定义在 `InterviewSession.status`。

```text
                        用户答题
active  -------------------------> checkpoint
  |                                  |
  | 用户直接结束                      | 点击整体评估
  |                                  v
  +------------------------------> evaluating
                                     |
                              成功   |   失败
                           +---------+---------+
                           v                   v
                        finished            stopped
```

各状态含义：

| 状态 | 含义 | 能否继续回答 |
| --- | --- | --- |
| `active` | 面试进行中 | 可以 |
| `checkpoint` | 已答满 12 题，等待用户选择 | 不可以，需开始新一轮 |
| `evaluating` | 正在调用大模型生成报告 | 不可以 |
| `finished` | 报告已生成并保存 | 不可以 |
| `stopped` | 评估过程失败或会话被停止 | 不能按正常流程继续 |

删除会话不会变成 `finished`。`DELETE /api/interviews/<id>` 会直接从内存移除会话，停止视觉后台线程，不生成报告。

## 18. 为什么刷新页面后有时找不到面试

正在进行的面试只放在 `SessionStore._sessions` 这个内存字典里：

```python
self._sessions: Dict[str, InterviewSession] = {}
```

这意味着：

- 关闭浏览器再刷新，只要后端还在运行，会话通常仍在。
- 重启 `backend.run`，所有未完成会话都会消失。
- 已生成并保存的报告不会消失，因为它们已经写入 `runtime/reports`。
- `SessionStore.cleanup()` 会清理创建超过约 8 小时的旧会话，目前在下一次创建会话时触发。

如果访问面试接口返回“会话不存在”，先确认后端有没有重启，再看会话 ID 是否和创建时一致。

## 19. 推荐的源码阅读顺序

不要一开始就打开 `interview.py` 从头看到尾，那样很容易迷路。建议按照下面顺序读。

### 第一遍：只看“路”，不看复杂算法

1. `backend/run.py`
   - 找到真正入口 `app = create_app()`。
2. `backend/app/__init__.py`
   - 看 `create_app()` 怎样创建服务和注册蓝图。
3. `backend/app/api/__init__.py`
   - 看五个子蓝图怎样组合。
4. `backend/app/api/common.py`
   - 看 `services()`、`login_required()`、`json_body()`。
5. `backend/app/api/system.py`
   - 最简单的蓝图示例，只有一个健康检查接口。

读完这五个文件，你就已经知道请求怎样进入各个模块了。

### 第二遍：一条简单业务线

1. `backend/app/api/auth.py`
2. `backend/app/services/auth_service.py`
3. `data/auth/users.json`

目标不是看懂密码算法，而是看懂：

```text
路由函数
  -> services().auth
  -> AuthService 方法
  -> 文件数据
  -> 返回结果
```

### 第三遍：面试主线

1. `backend/app/api/interview.py`
2. `backend/app/storage/session_store.py`
3. `backend/app/services/interview_service.py`
4. `backend/app/services/deepseek_service.py`
5. `backend/app/services/question_bank.py`

先只看接口里的函数顺序，不要一开始研究提示词的全部内容。提示词可以最后再看。

### 第四遍：语音、视觉和报告

1. `backend/app/api/media.py`
2. `backend/app/services/asr_service.py`
3. `backend/app/services/tts_service.py`
4. `backend/app/services/vision/worker.py`
5. `backend/app/services/vision/manager.py`
6. `backend/app/services/vision/session_analyzer.py`
7. `backend/app/services/score_service.py`
8. `backend/app/storage/report_store.py`

## 20. 怎样快速追踪一个功能

### 方法一：从前端 URL 找后端路由

假设前端调用：

```javascript
apiRequest(`/api/interviews/${id}/messages`, ...)
```

去掉 `/api`，剩下：

```text
/interviews/<id>/messages
```

然后在后端搜索：

```powershell
rg -n "interviews/.*/messages|send_interview_message" backend/app
```

很快就能定位到 `api/interview.py`。

### 方法二：从 `services()` 往后找

看到：

```python
services().resumes.save(...)
```

这样拆开理解：

```text
services()   -> ServiceContainer
.resumes     -> container.py 中的 self.resumes
.save(...)   -> ResumeService.save(...)
```

再打开 `services/resume_service.py` 找 `def save`。

### 方法三：从错误文本反查

在项目根目录执行：

```powershell
rg -n "错误提示中的一小段文字" backend
```

错误提示通常能直接定位到抛出异常的 Service 或返回错误的 Route。

## 21. 修改代码时应该改哪一层

| 想做的修改 | 通常改哪里 |
| --- | --- |
| 新增一个 HTTP 接口 | 对应 `api/*.py` 加路由，再在 Service 中加业务方法 |
| 修改接口地址 | `api/*.py` 的路由字符串；必要时同步前端路径 |
| 修改参数检查 | 对应 `api/*.py` 的视图函数 |
| 修改账号保存方式 | `services/auth_service.py` |
| 修改简历解析方式 | `services/resume_service.py` |
| 修改提问提示词 | `services/interview_service.py` |
| 修改题库抽题规则 | `services/question_bank.py` |
| 修改 DeepSeek 请求 | `services/deepseek_service.py` |
| 修改摄像头分析算法 | `services/vision/` |
| 修改报告保存格式 | `services/score_service.py`、`storage/report_store.py` |
| 修改端口和路径 | `app/config.py` 或根目录 `.env` |

判断原则很简单：

- “这个请求是否允许、参数对不对”通常属于 API 层。
- “事情具体怎么做”通常属于 Service 层。
- “做完以后存哪里”通常属于 Storage 层。

## 22. 两个叫 session 的东西不要弄混

项目里有两个名字很像的概念。

### Flask `session`

它来自：

```python
from flask import session
```

它表示浏览器登录状态，最终主要保存在签名 Cookie 中。代码常用：

```python
session["user_id"]
```

作用和“当前浏览器是哪位用户登录了”有关。

### `InterviewSession`

它来自：

```python
from backend.app.storage.session_store import InterviewSession
```

它表示一次正在进行的面试，包括：

- 面试 ID 和用户 ID。
- 岗位、难度和音色。
- 对话记录。
- 当前题号、场景和追问状态。
- 摄像头分析对象。
- 报告和结束状态。

它主要放在 `SessionStore` 的内存字典中。

一句口诀：

```text
Flask session  = 谁登录了
InterviewSession = 谁正在参加哪场面试
```

## 23. 常见问题排查表

| 现象 | 最可能原因 | 先看哪里 |
| --- | --- | --- |
| 接口返回 404 | 路径拼错、蓝图前缀不对 | `api/*.py` 路由、`api/__init__.py` |
| 接口返回 401 | 未登录、Cookie 失效 | `common.py::login_required` |
| 接口返回 409 | 面试已结束、状态不允许 | `interview.py` 的状态检查 |
| 接口返回 502 | DeepSeek 调用失败 | `deepseek_service.py`、`interview_service.py` |
| 刷新后会话不存在 | 后端重启或会话超过 8 小时 | `session_store.py` |
| `/api/health` 中 `deepseek=false` | 没有配置 API Key | 根目录 `.env` |
| 人脸登录无法使用 | 人脸依赖或人脸模型未加载 | `face_recognition.py` |
| 语音识别慢 | Whisper 第一次加载模型 | `asr_service.py` |
| 摄像头没有指标 | 浏览器未提交帧或 MediaPipe 失败 | `frames` 接口、`vision/worker.py` |
| 报告列表为空 | 还没有完成面试，或用户不同 | `runtime/reports/<用户 ID>` |
| 页面能开但 API 404 | 前端路径多了或少了一级 | `frontend/src/api/client.js` 和后端蓝图 |

可以先用这个接口检查后端能力：

```text
GET http://127.0.0.1:8765/api/health
```

它会返回 DeepSeek、Whisper、Edge-TTS、MediaPipe、题库和前端构建是否可用。

## 24. 一张纸记住整个后端

```text
启动：
  run.py
    -> create_app()
    -> ServiceContainer()
    -> register_blueprint(api, url_prefix="/api")

请求：
  Waitress
    -> Flask 路由匹配
    -> api/*.py 的视图函数
    -> services().某个工具
    -> services/*.py 的真正业务代码
    -> data/、runtime/、模型或外部 API
    -> jsonify() / send_file()

面试：
  创建会话
    -> 生成的面试状态放内存
    -> 摄像头由后台线程分析
    -> 每轮回答调用 _next_plan()
    -> 生成下一题
    -> 12 题进入 checkpoint
    -> finish 调用 DeepSeek 评估
    -> 融合问答、表情、注意力分数
    -> 保存 JSON 报告

阅读口诀：
  先找入口，再找蓝图；
  路由负责接客，Service 负责办事；
  Container 负责给工具，Storage 负责存取；
  跟着一个请求走，永远比一次看完全部文件快。
```

如果你以后又看不懂一个接口，只需要回答四个问题：

1. 浏览器请求的是哪个 URL？
2. 哪个蓝图的哪个路由处理它？
3. 路由调用了 `services()` 里的哪个工具？
4. 这个工具最后访问了哪个文件、模型或外部 API？

把答案连成箭头，就是完整的执行顺序。
