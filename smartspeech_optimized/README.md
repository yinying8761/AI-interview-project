# 智能体模拟面试系统

这是一个本地运行的 AI 模拟面试训练平台。系统结合目标岗位、面试难度、候选人简历和本地题库生成面试问题，并通过语音识别、语音合成、实时视频状态分析生成复盘报告。

前端使用 Vue 3 + Vite，后端使用 Flask + Waitress。默认岗位为 **Java 后端开发工程师**。

## 核心能力

- 账号密码登录、注册、本地人脸注册和人脸登录。
- 上传 PDF、DOC、DOCX 简历，自动提取项目、职责、技术栈和成果文本。
- 自动恢复当前账号上次上传的简历，也支持重新上传或移除。
- 根据岗位、难度、简历内容和计算机基础场景生成面试问题。
- 场景包括多线程与多进程、并发、项目异常处理、数据库索引与优化、Redis、RAG、数据结构算法、团队协作、AI 工程化落地等。
- 每个场景随机追问 1-3 次，也可固定为 1、2 或 3 次，硬上限为 3 次。
- 有简历时，简历项目和基础场景穿插提问；追问达到目标后切换场景或简历项目。
- 单轮最多 12 个问题。回答第 12 题后停止生成下一题，由用户选择整体评估或使用相同配置开启新一轮。
- 本地 JSON 题库支持分类、去重抽题和按场景抽题，不向候选人暴露题库来源或参考答案。
- Whisper 将语音回答转写为文字，Edge-TTS 朗读问题，Windows SAPI 提供语音回退。
- MediaPipe 实时分析表情、视线、头部姿态和注意力，浏览器只提交降采样后的画面。
- 面试结束后生成问答、表情、注意力及综合评分报告，历史报告可查看和打印。

## 系统架构

```text
Vue 3 + Pinia + Vue Router
        |
        | HTTP / JSON / Multipart
        v
Flask API + Waitress
        |
        +-- AuthService          账号、密码和人脸绑定
        +-- ResumeService        PDF / DOC / DOCX 文本提取
        +-- QuestionBankService  JSON 题库分类与抽题
        +-- InterviewService     提示词、状态机和大模型调用
        +-- ASRService           Whisper 语音转写
        +-- TTSService           Edge-TTS / Windows SAPI
        +-- VisionManager        MediaPipe 实时状态分析
        +-- ReportStore          本地评估报告
        |
        +-- data/                账号、简历解析文本、题库和持久化密钥
        +-- runtime/             临时音频、语音缓存和面试报告
```

## 目录结构

```text
smartspeech_optimized_new/
|-- backend/
|   |-- app/
|   |   |-- api/          Flask 接口
|   |   |-- services/     面试、简历、题库、语音、视觉和认证服务
|   |   `-- storage/      会话与报告存储
|   |-- tests/            后端单元测试
|   `-- run.py            Waitress 启动入口
|-- frontend/
|   |-- src/
|   |   |-- components/   工作台组件
|   |   |-- pages/        登录、面试和报告页面
|   |   |-- stores/       Pinia 状态
|   |   `-- api/          前端 API 客户端
|   `-- package.json
|-- data/
|   `-- interview_question_bank.json
|-- runtime/              本地运行数据
|-- scripts/
|   `-- import_question_bank.py
|-- .env.example
|-- requirements.txt
`-- start_system.bat
```

## 环境要求

- Windows 10/11。
- Python 3.10，推荐使用本机已配置的 `nlp_env`。
- Node.js 20 或更高版本，以及 npm。
- Chrome、Edge 等支持摄像头和 MediaStream 的现代浏览器。
- 可访问 DeepSeek API 的网络环境，用于生成问题、追问和综合评估。
- DOC 文件解析需要本机安装 Microsoft Word；推荐优先上传 DOCX 或 PDF。

项目默认使用以下 Python 解释器：

```text
D:\Work_Software\Anaconda3\envs\nlp_env\python.exe
```

## 快速启动

1. 如首次使用，复制 `.env.example` 为 `.env`，并填写 `DEEPSEEK_API_KEY`。
2. 双击项目根目录的 `start_system.bat`。
3. 脚本会检查 Python 依赖、构建 Vue 前端、启动 Flask + Waitress，并打开浏览器。
4. 默认访问地址为 `http://127.0.0.1:8765`。

首次加载 Whisper 模型可能需要下载模型文件。后续启动会复用已经安装的依赖和模型缓存。

## 手动启动

### 生产模式

```powershell
cd "D:\Project_Code_2026\tyut_comprehensive_pratice\smartspeech_optimized_new"

& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m pip install -r requirements.txt

cd frontend
npm ci
npm run build
cd ..

"D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m backend.run
```

浏览器打开 `http://127.0.0.1:8765`。

### 前后端开发模式

先启动后端：

```powershell
& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m backend.run
```

再打开另一个终端启动 Vite：

```powershell
cd frontend
npm run dev
```

开发地址为 `http://127.0.0.1:5173`。Vite 会把 `/api` 请求代理到后端 `8765` 端口。

## 简历功能

面试配置页提供“简历来源”区域：

- 首次上传支持 PDF、DOC、DOCX，默认最大 8 MB。
- 上传成功后只保存解析后的文本、文件名、大小和上传时间，不保留原始文件。
- 再次进入页面时自动恢复当前账号最近一次上传的简历。
- 可以在开始面试前重新上传或移除简历。
- 开始面试时必须传入当前账号持有的 `resume_id`，后端会再次校验归属。
- 大模型只看到截断后的简历文本。该文本被标记为面试依据，不会被当作可执行指令。

扫描版 PDF 不包含可提取文本，系统会提示改为文本型 PDF、DOCX 或 DOC。DOC 解析依赖 Microsoft Word 和 `pywin32`。

## 面试流程

后端状态机负责强制执行流程，不依赖大模型自行记忆规则：

1. 有简历时，第一题从简历中最值得核实的项目或经历开始；没有简历时，从第一个基础场景开始。
2. 每个场景开始前随机选择 1-3 次追问目标，也可按配置固定为 1、2 或 3 次。
3. 当前追问次数未达到目标时，下一题只能承接当前场景继续追问。
4. 达到追问目标后，有简历时下一次切换到简历中的其他项目；没有简历时进入下一个基础场景。
5. 简历项目和基础场景穿插提问。基础题从本地题库按场景抽取，场景主问题直接采用题库原文，保证不会串回上一轮项目追问。
6. 全场问题数上限为 12。回答第 12 题后接口返回 `checkpoint.required=true`，不会生成第 13 题。
7. 检查点弹窗支持“整体评估”和“继续下一轮”。继续下一轮会使用相同岗位、难度、简历和追问配置创建新会话，题号与追问次数重新计算。

## 本地题库

原始题库文件：

```text
科大讯飞消费者BG面试题（整合版）.docx
```

运行以下命令将 DOCX 转为结构化 JSON：

```powershell
& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" scripts\import_question_bank.py
```

默认生成：

```text
data/interview_question_bank.json
```

JSON 便于版本管理、人工校对和后续扩充。`QuestionBankService` 会按场景主题筛选未使用题目；当前场景无未用题时，会从其他未用题中补选，不会重复使用已抽题目。

## 环境变量

配置统一保存在根目录 `.env`。常用配置如下：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek API 密钥 |
| `DEEPSEEK_API_URL` | `https://api.deepseek.com/chat/completions` | 对话接口地址 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | 面试智能体模型 |
| `DEEPSEEK_TIMEOUT` | `90` | 大模型请求超时秒数 |
| `CHAT_CONTEXT_MESSAGES` | `20` | 发送给大模型的最近对话条数 |
| `MAX_RESUME_SIZE` | `8388608` | 简历大小上限，单位为字节 |
| `RESUME_MAX_EXTRACTED_CHARS` | `50000` | 允许保存的简历文本最大字符数 |
| `RESUME_PROMPT_MAX_CHARS` | `24000` | 每次发送给大模型的简历文本上限 |
| `INTERVIEW_QUESTION_LIMIT` | `12` | 单轮面试最多问题数 |
| `WHISPER_MODEL_SIZE` | `small` | Whisper 模型规格 |
| `WHISPER_DEVICE` | `cpu` | Whisper 推理设备 |
| `DEFAULT_TTS_VOICE` | `zh-CN-YunxiNeural` | 默认面试官音色 |
| `DEFAULT_VISION_FPS` | `3` | 默认视频分析帧率 |
| `MAX_VISION_FPS` | `8` | 视频分析帧率上限 |
| `SNAPSHOT_INTERVAL_SECONDS` | `2` | 指标快照间隔 |
| `FACE_MATCH_THRESHOLD` | `1.0` | 人脸特征匹配阈值 |
| `APP_HOST` | `127.0.0.1` | 服务监听地址 |
| `APP_PORT` | `8765` | 服务端口 |
| `FLASK_SECRET_KEY` | 自动生成 | 会话签名密钥 |

不要提交或公开 `.env`、`data/.flask_secret`、`data/auth/` 和 `data/resumes/`。

## 主要接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查和能力探测 |
| `POST` | `/api/auth/register` | 注册账号 |
| `POST` | `/api/auth/login` | 密码登录 |
| `POST` | `/api/auth/face-register` | 绑定人脸 |
| `POST` | `/api/auth/face-login` | 人脸登录 |
| `POST` | `/api/resumes` | 上传并解析简历 |
| `GET` | `/api/resumes/latest` | 获取当前账号最近一次简历摘要 |
| `DELETE` | `/api/resumes/latest` | 删除当前账号最近一次简历 |
| `POST` | `/api/interviews` | 创建面试会话 |
| `POST` | `/api/interviews/<id>/opening` | 生成开场和第一题 |
| `POST` | `/api/interviews/<id>/messages` | 提交回答并生成下一题 |
| `POST` | `/api/interviews/<id>/frames` | 提交实时视频帧 |
| `GET` | `/api/interviews/<id>/metrics` | 获取实时分析指标 |
| `POST` | `/api/interviews/<id>/finish` | 结束面试并生成报告 |
| `GET` | `/api/reports` | 获取历史报告 |
| `GET` | `/api/reports/<id>` | 获取报告详情 |
| `POST` | `/api/media/transcribe` | 语音转写 |
| `POST` | `/api/media/tts` | 语音合成 |

## 评分规则

```text
最终得分 = 问答得分 × 80% + 视频综合得分 × 20%
视频综合得分 = 表情得分 × 50% + 注意力得分 × 50%
```

有效视频样本不足时，系统保留视频得分为空，不重新分配权重制造虚高成绩。

## 测试与构建

运行后端完整单元测试：

```powershell
& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m unittest discover -s backend\tests -v
```

检查 Python 语法：

```powershell
& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m compileall backend
```

构建前端：

```powershell
cd frontend
npm run build
```

检查 Python 依赖：

```powershell
& "D:\Work_Software\Anaconda3\envs\nlp_env\python.exe" -m pip check
```

## 隐私说明

- 摄像头画面只用于浏览器显示和后端实时分析，不写入磁盘。
- 人脸注册只保存特征向量，不保存原始照片。
- 回答录音完成转写后立即删除。
- 简历只保存解析文本和元数据，不保存上传的原始文件。
- 面试报告只保存问答文本、评分、聚合指标和状态变化。
- 账号、简历文本和报告保存在本机项目目录中。

## 常见问题

### 简历上传后没有文字

扫描图片型 PDF 无法直接提取文本。请上传文本型 PDF、DOCX 或 DOC，或者先使用 OCR 工具转换。

### DOC 文件无法解析

安装 Microsoft Word 并执行 `pip install -r requirements.txt`。如果不方便安装 Word，请将文件另存为 DOCX 或 PDF。

### 页面提示摄像头不可用

检查浏览器摄像头权限，关闭占用摄像头的会议或录制软件，然后点击页面中的重试操作。远程访问时，浏览器通常要求 HTTPS 才能使用摄像头。

### 开始面试后页面卡顿

在面试配置中选择 `2 帧/秒 · 低负载`。系统也会根据后端处理延迟自动降低采样频率。

### 提问很慢或提示大模型不可用

检查 `.env` 中的 `DEEPSEEK_API_KEY`、网络连接和接口地址。首题生成失败时会使用备用问题，但连续追问和综合评估仍需要可用的大模型服务。

### 首次语音回答等待较久

首次使用 Whisper 时需要下载或加载模型，耗时取决于模型规格和网络速度。CPU 环境建议使用 `small` 或更小规格。

### 端口 8765 已被占用

如果是本系统，直接打开 `http://127.0.0.1:8765`。如果是其他程序，修改 `.env` 中的 `APP_PORT`。

## 项目文档

项目根目录提供 Word 版系统说明和接口文档：

```text
智能模拟面试系统介绍、原理、应用及操作教程.docx
智能模拟面试系统接口清单及接口文档.docx
```
