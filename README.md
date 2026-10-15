# 多模态智能模拟面试评测平台

太原理工就业班 · 项目化教学（人工智能 Ⅱ 方向）· 第 11 组「面面俱到项目组」

| 成员 | 项目角色 |
| --- | --- |
| 郑颖浩（组长） | 项目管理、架构设计、开发、部署 |
| 常辰 | AI 能力开发、感知模块开发 |
| 赵佳伟 | 前端开发、前后端联调、测试 |

---

## 一、系统构成

系统由「一个主后端 + 一个 AI 能力服务 + 两个前端」组成。浏览器只与 Java 主后端交互，
Java 主后端通过 `AiApiClient` 调用 Python AI 能力服务完成大模型、视觉与语音能力，
Python 服务不直接对外暴露。

| 模块 | 目录 | 技术栈 | 端口 |
| --- | --- | --- | --- |
| Java 主后端 | `interview-ai/` | Spring Boot 3.5 · MyBatis-Plus · MySQL 8 · Redis · JWT · WebSocket | 8080 |
| AI 能力服务 | `smartspeech_optimized/` | Flask · DeepSeek · MediaPipe · Edge-TTS | 8765 |
| 用户端前端 | `interview-ai/frontend/` | Vue 3 · Vite · Element Plus · Pinia | 5173 |
| 管理端前端 | `interviwe-platform/interview-platform/` | Vue 3 · Vite | 5174（需手动指定） |

Java 后端为 Maven 多模块结构：`interview-common`（公共基础）、`interview-modules`（六个业务模块
system / question / interview / resume / file / job）、`interview-bootstrap`（启动与配置）。

### 感知模块与扩展能力

课程要求「三选二」感知模块 + 一项扩展能力，本组实现：

- **文本理解与评价** —— 对接 DeepSeek 大模型面试官，逐题评估并生成报告；
- **视觉行为分析** —— MediaPipe FaceMesh 采集表情、视线、头姿与注意力指标；
- **扩展能力：大模型面试官** —— 开场出题、追问、评分融合（问答 × 80% + 视频 × 20%）。

视频不可用或未采集时，报告对应维度显示「未评估」，不计入融合分。

---

## 二、运行步骤

### 0. 前置依赖

- JDK 21、Maven 3.9+
- Python 3.10（3.11+ 未验证；`mediapipe` 需 0.10.14）
- Node.js 18+
- MySQL 8、Redis（Windows 下均可作为服务常驻）

### 1. 初始化数据库

```bash
mysql -uroot -p < interview-ai/sql/interview_ai.sql
```

如需在已有库上做增量更新，按顺序执行 `interview-ai/sql/` 下其余 `*_delta*.sql`。

### 2. 配置密钥

**Java 侧**：复制模板并填写本机口令（该文件已被 `.gitignore` 忽略）

```bash
cd interview-ai/interview-bootstrap/src/main/resources
cp application-local.yml.example application-local.yml
# 编辑 application-local.yml：填写 MySQL 口令；Gitee 登录可选
```

`application.yml` 通过 `spring.profiles.active: dev,local` 载入该文件，
其优先级高于 `application-dev.yml` 中的占位符。

**Python 侧**：复制模板并填写 DeepSeek API Key

```bash
cd smartspeech_optimized
cp .env.example .env
# 编辑 .env：DEEPSEEK_API_KEY=sk-xxxx
```

### 3. 启动 AI 能力服务（8765）

```bash
cd smartspeech_optimized
python -m backend.run
```

启动后访问 <http://127.0.0.1:8765/api/ai/health> 应返回各能力开关状态。

### 4. 构建并启动 Java 主后端（8080）

```bash
cd interview-ai
mvn clean package -DskipTests
java -jar interview-bootstrap/target/interview-bootstrap-1.0.0.jar
```

接口文档：<http://127.0.0.1:8080/doc.html>

### 5. 启动前端

```bash
# 用户端
cd interview-ai/frontend && npm install && npm run dev     # http://localhost:5173

# 管理端
cd interviwe-platform/interview-platform && npm install && npm run dev -- --port 5174
```

### 6. 演示账号

| 账号 | 口令 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 管理员 |
| `host01` | `123456` | 面试官 |
| `test01` | `123456` | 候选人 |

> Python AI 服务另有一套独立的账号体系（存于 `data/auth/users.json`，已被忽略），
> 与 Java 侧不互通；正常演示只使用上表账号。

---

## 三、隐私与安全约定

本仓库**不含任何真实密钥与用户隐私数据**，以下内容均已在 `.gitignore` 中排除：

| 类别 | 具体内容 |
| --- | --- |
| 密钥配置 | `application-local.yml`（数据库口令、Gitee client-secret）、`.env`（DeepSeek API Key） |
| 用户隐私 | `data/auth/`（账号与口令哈希）、`data/auth/faces/`（人脸特征）、`data/resumes/`、`runtime/`（报告、转写音频、截图） |
| 会话凭据 | `qa-state.json`（含已登录的会话 Cookie） |
| 数据库转储 | `*backup*.sql` 等全库导出（含口令哈希与考生作答原文） |
| 运行日志 | `*.log` |

仓库中 `application-dev.yml` 的第三方密钥一律为占位值，请勿把真实值直接写回该文件。

`smartspeech_optimized/ffmpeg.exe` 体积达 70MB 且可自动补齐，故未入库；
代码在找不到它时会通过 `imageio-ffmpeg` 自动准备，无需人工干预。

---

## 四、补充说明

- `interviwe-platform/` 为管理端前端工程，部分页面仍在从本地 mock 数据切换到真实接口的过程中。
- `smartspeech_optimized/` 自带一个历史遗留的独立前端界面（同端口 8765），
  属于该 AI 服务早期作为独立产品时的产物，**不属于本系统架构**，演示时不使用。
