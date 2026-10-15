# 多模态智能模拟面试评测平台 v1.1.0

第 11 组“面面俱到项目组”；组长郑颖浩，成员常辰、赵佳伟。课程验收选定 **文本理解与评价＋视觉行为分析** 两个感知模块，扩展能力为 **大模型面试官**。用户端 Vue 3、主服务 Spring Boot、AI 服务 Flask／DeepSeek／MediaPipe；演示需 JDK 21、Maven 3.9+、Node.js 18+、Python 3.12、Redis。AI 环境使用 `smartspeech_optimized/requirements-acceptance.txt`；原 `requirements.txt` 还包含未选中的语音等可选功能。

## 本地启动（无需 MySQL）

1. 保持本机 Redis 在 `127.0.0.1:6379` 运行。把 `smartspeech_optimized/.env.example` 复制为 `.env`，仅在本机填写 `DEEPSEEK_API_KEY`；绝不要把 `.env` 放进交付包。在 PowerShell 进入 `smartspeech_optimized/`，首次运行执行 `py -3.12 -m venv .venv-vision` 和 `.\.venv-vision\Scripts\python.exe -m pip install -r requirements-acceptance.txt`；以后直接执行 `.\.venv-vision\Scripts\python.exe -m backend.run`。不要从 Conda `base` 环境直接运行 `python -m backend.run`。AI 服务地址为 `http://127.0.0.1:8765`。
2. 从项目根目录执行 `mvn -f .\interview-ai\pom.xml -B -ntp package`；重新打包前先停止正在运行的 Java 服务，避免 Windows 锁住旧 JAR。随后进入 `interview-ai/`，执行 `java -jar interview-bootstrap/target/interview-bootstrap-1.0.0.jar --spring.profiles.active=dev,demo --server.port=8088`。此模式在 `interview-ai/runtime/` 建立持久 H2 演示库，首次运行自动导入场景、题目和演示账号。接口文档：`http://127.0.0.1:8088/doc.html`。
3. 在 `interview-ai/frontend/` 执行 `npm ci`；PowerShell 中执行 `$env:API_PROXY_TARGET='http://127.0.0.1:8088'; npm run dev`，打开 `http://127.0.0.1:5173`。候选人 `test01 / 123456`，面试官 `host01 / 123456`，管理员 `admin / admin123`；这些仅为本地演示账号。

## 现场验收

候选人登录后在“面试大厅”选择 Java 后端场景，面试类型选“大模型面试官（文本＋视觉）”。留空预约时间可立即进入；填写预约时间可测试持久化、刷新查询及角色权限。进入面试后点击“生成首题”，输入一段新的岗位回答，点击提交；允许摄像头并保持面部可见约 15 秒以上以采集视觉样本，随后完成面试。在“我的记录→查看报告”检查原始问答、两项评测状态、分数和公式，也可下载完整 JSON。未采集到有效视觉样本时，该维度与综合分均为“未评估”。

附带 `data/测试数据/合成面试人像.png` 为**虚构人物**，用于无摄像头的视觉接口自动化复测；文本输入见 `data/测试数据/新文本回答.txt`。`tools/smoke_test.py` 覆盖登录、预约、权限和异常；`tools/smoke_ai.py` 验证真实出题与问答评分；`tools/smoke_vision.py` 把合成图像逐帧送入 MediaPipe 并检查融合报告。运行三者需要已启动服务及 Python `requests`、`Pillow`。

## 可选：改用 MySQL 虚拟机

在 MySQL 8 建立 `interview_ai` 库并执行 `interview-ai/sql/interview_ai.sql`。复制 `interview-ai/interview-bootstrap/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填写虚拟机地址和数据库口令；然后用 `--spring.profiles.active=dev,local` 启动 Java 服务。该配置文件不得提交。若 `3306` 不通，请检查虚拟机网络、MySQL 监听与防火墙；本地 H2 演示模式可以继续验收。已有旧库只执行 `interview-ai/sql/acceptance_v110_delta.sql` 作增量更新。

项目只评估练习表现，视觉指标不是身份认证。图像仅用于本次实时分析，报告保存汇总指标及输入追溯，不保存原始摄像头帧。真实 API 密钥、个人材料中的班级／学号、个人贡献证明需在提交前由成员本人核实。
